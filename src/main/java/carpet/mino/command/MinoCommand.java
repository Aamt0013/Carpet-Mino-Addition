package carpet.mino.command;

import carpet.CarpetServer;
import carpet.api.settings.CarpetRule;
import carpet.api.settings.InvalidRuleValueException;
import carpet.api.settings.SettingsManager;
import carpet.mino.settings.MinoSettings;
import carpet.mino.settings.MobDropSpec;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.Identifier;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * {@code /mino} 命令树。
 *
 * <p>规则值本身支持 {@code 3,cow=10} 这种写法，但手打要记语法和生物 id，
 * 这里提供带补全的等价入口：
 *
 * <pre>
 * /mino drop &lt;生物&gt; &lt;倍率&gt;   设置单个生物
 * /mino drop all &lt;倍率&gt;      设置全局倍率（作用于所有生物）
 * /mino drop list            查看当前设置
 * /mino drop clear           全部恢复原版
 * </pre>
 *
 * <p>写入走 {@link CarpetRule#set(CommandSourceStack, String)}，校验与持久化都会正常触发，
 * 效果与手写 {@code /carpet mobDropMultiplier ...} 等价。
 */
public final class MinoCommand {
	/** 命令里表示「全局倍率」的关键字。 */
	private static final String ALL_KEYWORD = "all";

	private MinoCommand() {
	}

	public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
		dispatcher.register(
				Commands.literal("mino")
						.requires(source -> source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
						.then(Commands.literal("drop")
								.then(Commands.literal("list").executes(MinoCommand::listSpec))
								.then(Commands.literal("clear").executes(MinoCommand::clearSpec))
								.then(Commands.literal(ALL_KEYWORD)
										.then(Commands.argument("multiplier", multiplierArg())
												.executes(MinoCommand::setGlobal)))
								.then(Commands.argument("mob", StringArgumentType.string())
										.suggests(MinoCommand::suggestMobIds)
										.then(Commands.argument("multiplier", multiplierArg())
												.executes(MinoCommand::setMob))))
		);
	}

	private static IntegerArgumentType multiplierArg() {
		return IntegerArgumentType.integer(MobDropSpec.MIN_MULTIPLIER, MobDropSpec.MAX_MULTIPLIER);
	}

	/** 补全生物 id：原版只提示 path，模组生物带命名空间，前缀匹配。 */
	private static CompletableFuture<Suggestions> suggestMobIds(CommandContext<CommandSourceStack> context, SuggestionsBuilder builder) {
		String remaining = builder.getRemaining().toLowerCase(Locale.ROOT);
		List<String> ids = new ArrayList<>();
		for (Identifier id : BuiltInRegistries.ENTITY_TYPE.keySet()) {
			// 原版省略 minecraft: 前缀，模组生物保留完整 id，两者都能被 MobDropSpec 解析。
			String candidate = id.getNamespace().equals("minecraft") ? id.getPath() : id.toString();
			if (candidate.startsWith(remaining)) {
				ids.add(candidate);
			}
		}
		ids.sort(String::compareTo);
		return SharedSuggestionProvider.suggest(ids, builder);
	}

	private static int setMob(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		String rawMob = StringArgumentType.getString(context, "mob");
		int multiplier = IntegerArgumentType.getInteger(context, "multiplier");

		Identifier id = MobDropSpec.resolveEntityId(rawMob);
		if (id == null) {
			source.sendFailure(tr("mino.command.unknown_mob", rawMob));
			return 0;
		}

		MobDropSpec current = currentSpec();
		// 倍率 1 只有在全局也是 1 时才等价于「没有覆盖」，此时删掉条目避免 cow=1 这种噪音。
		// 全局不是 1 时必须显式写入 cow=1，否则删掉后该生物会退回全局倍率。
		MobDropSpec updated = multiplier == MobDropSpec.MIN_MULTIPLIER && current.global() == MobDropSpec.MIN_MULTIPLIER
				? current.withoutOverride(id.toString())
				: current.withOverride(id.toString(), multiplier);
		if (!apply(source, updated)) {
			return 0;
		}
		// 回显「中文名 = 倍率」，不再重复打印整串规则值。
		source.sendSuccess(() -> tr("mino.command.set_mob", entityName(id), multiplier), true);
		return 1;
	}

	private static int setGlobal(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		int multiplier = IntegerArgumentType.getInteger(context, "multiplier");
		if (!apply(source, currentSpec().withGlobal(multiplier))) {
			return 0;
		}
		source.sendSuccess(() -> tr("mino.command.set_all", multiplier), true);
		return 1;
	}

	private static int listSpec(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		MobDropSpec spec = currentSpec();
		Map<String, Integer> overrides = spec.overrides();

		if (overrides.isEmpty()) {
			source.sendSuccess(() -> tr("mino.command.list_global_only", spec.global()), false);
			return 1;
		}

		source.sendSuccess(() -> tr("mino.command.list_global", spec.global()), false);
		for (Map.Entry<String, Integer> entry : overrides.entrySet()) {
			Identifier id = Identifier.parse(entry.getKey());
			int value = entry.getValue();
			source.sendSuccess(() -> Component.literal("  ")
					.append(entityName(id))
					.append(Component.literal(" = " + value)), false);
		}
		return 1;
	}

	private static int clearSpec(CommandContext<CommandSourceStack> context) {
		CommandSourceStack source = context.getSource();
		if (!apply(source, MobDropSpec.vanilla())) {
			return 0;
		}
		source.sendSuccess(() -> tr("mino.command.cleared"), true);
		return 1;
	}

	/** 把 spec 写回规则。成功返回 true，失败时已经 sendFailure。 */
	private static boolean apply(CommandSourceStack source, MobDropSpec spec) {
		String value = spec.render();
		SettingsManager manager = CarpetServer.settingsManager;
		if (manager == null) {
			source.sendFailure(tr("mino.command.no_manager"));
			return false;
		}
		CarpetRule<?> rule = manager.getCarpetRule(MobDropSpec.RULE_NAME);
		if (rule == null) {
			source.sendFailure(tr("mino.command.no_rule"));
			return false;
		}
		try {
			// 必须显式转成 String 走 set(CommandSourceStack, String) 这个重载：
			// CarpetRule 同时有 set(CommandSourceStack, String) 和 set(CommandSourceStack, T)，
			// 而这里的 T 是通配符捕获，直接传 value 会被判为歧义调用。
			rule.set(source, (String) value);
			return true;
		} catch (InvalidRuleValueException e) {
			source.sendFailure(tr("mino.command.write_failed", String.valueOf(e.getMessage())));
			return false;
		}
	}

	/** 取生物的中文名（客户端语言决定），查不到时退回 id。 */
	private static Component entityName(Identifier id) {
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
		if (type == null) {
			return Component.literal(id.getPath());
		}
		return type.getDescription();
	}

	private static MutableComponent tr(String key, Object... args) {
		return Component.translatable(key, args);
	}

	/** 读取当前规则值并解析；非法值按原版处理，保证命令不会因为脏配置而崩。 */
	private static MobDropSpec currentSpec() {
		return MobDropSpec.parseOrVanilla(MinoSettings.mobDropMultiplier);
	}
}
