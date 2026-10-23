package carpet.mino;

import carpet.CarpetExtension;
import carpet.CarpetServer;
import carpet.mino.command.MinoCommand;
import carpet.mino.settings.MinoSettings;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Carpet Extension 实现。
 *
 * <p>规则注册进 Carpet 主 {@code SettingsManager} 而非自建一个：这样规则与 Carpet 本体平级，
 * 且翻译键落在 {@code carpet.rule.*}，与 Carpet 查找键一致。
 */
public class MinoExtension implements CarpetExtension {
	private static final Gson GSON = new Gson();
	private static final Type TRANSLATION_TYPE = new TypeToken<Map<String, String>>() {
	}.getType();

	private static boolean rulesRegistered;

	private static String modVersion() {
		return FabricLoader.getInstance()
				.getModContainer(CarpetMinoAddition.MOD_ID)
				.map(container -> container.getMetadata().getVersion().getFriendlyString())
				.orElse("unknown");
	}

	/**
	 * 把规则注册进 Carpet 主规则管理器。
	 *
	 * <p>必须在 {@code onGameStarted} 里调用：{@code parseSettingsClass} 内部会触发
	 * {@code Translations.updateLanguage()}，它遍历 {@code CarpetServer.extensions} 收集翻译，
	 * 此时本扩展已在列表中。若提前到静态初始化，会因找不到翻译键而抛 NPE。
	 */
	private static synchronized void registerRules() {
		if (rulesRegistered) {
			return;
		}
		rulesRegistered = true;
		CarpetServer.settingsManager.parseSettingsClass(MinoSettings.class);
	}

	@Override
	public String version() {
		return modVersion();
	}

	@Override
	public void onGameStarted() {
		registerRules();
	}

	@Override
	public void registerCommands(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext context) {
		MinoCommand.register(dispatcher);
	}

	/**
	 * 向 Carpet 提供本扩展的翻译。
	 *
	 * <p>Carpet 只自动加载自己命名空间下的 {@code assets/carpet/lang/<lang>.json}，
	 * 扩展必须走这个回调。返回 null 表示本扩展对该语言没有翻译。
	 *
	 * <p>本模组的 lang 文件统一写绝对键（{@code carpet.rule.*}、{@code carpet.category.*}），
	 * Carpet 对已带 {@code carpet.} 前缀的键原样保留。
	 */
	@Override
	public Map<String, String> canHasTranslations(String lang) {
		InputStream stream = MinoExtension.class.getClassLoader()
				.getResourceAsStream("assets/" + CarpetMinoAddition.MOD_ID + "/lang/" + lang + ".json");
		if (stream == null) {
			return null;
		}

		try (InputStream in = stream) {
			Map<String, String> map = GSON.fromJson(
					new InputStreamReader(in, StandardCharsets.UTF_8),
					TRANSLATION_TYPE
			);
			return map == null ? null : map;
		} catch (IOException e) {
			CarpetMinoAddition.LOGGER.warn("Failed to read translations for language {}", lang, e);
			return null;
		}
	}
}
