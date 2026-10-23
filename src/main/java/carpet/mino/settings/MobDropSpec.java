package carpet.mino.settings;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * {@link MinoSettings#mobDropMultiplier} 规则值的解析结果与渲染器。
 *
 * <p>一个功能只需要一条规则，全局默认值和单生物覆盖合并进同一个字符串里：
 * <pre>
 * ""               → 全部原版
 * "3"              → 全局 3 倍，所有生物
 * "cow=10"         → 只有牛 10 倍，其余原版
 * "3,cow=10"       → 全局 3 倍，牛单独 10 倍
 * "all=3,cow=10"   → 同上，all= 是全局键的显式写法
 * </pre>
 *
 * <p>语法要点：
 * <ul>
 *   <li>裸数字（{@code 3}）或 {@code all=3} / {@code default=3} / {@code *=3} 表示全局倍率</li>
 *   <li>{@code 生物id=倍率} 表示单生物覆盖，优先于全局</li>
 *   <li>倍率取值 1-1999</li>
 *   <li>生物 id 可省略 {@code minecraft:} 前缀，但非原版生物必须写全命名空间</li>
 * </ul>
 *
 * <p>覆盖表的 key 存完整 id（含命名空间），渲染时 {@code minecraft:} 前缀省略、其它命名空间写全，
 * 这样 {@code mymod:foo=10} 才能被 {@link #parse} 原样读回。
 *
 * <p>解析与渲染共用本类，保证「校验通过的写法」和「运行时读到的值」永远一致；
 * 渲染会归一化（{@code all=3} 写成 {@code 3}），所以命令回显可能与手打略有不同。
 */
public final class MobDropSpec {
	/**
	 * 规则名，必须与 {@link MinoSettings#mobDropMultiplier} 的字段名一致。
	 *
	 * <p>命令层按名字反查规则，这里集中定义一处，避免改字段名时漏改字符串。
	 */
	public static final String RULE_NAME = "mobDropMultiplier";

	public static final int MIN_MULTIPLIER = 1;
	/**
	 * 倍率上限。
	 *
	 * <p>定义在这里而不是 validator 里：validator 依赖本类做解析，
	 * 反向引用会形成类初始化循环，上限被读成 0。
	 */
	public static final int MAX_MULTIPLIER = 1999;

	/** 原版命名空间。渲染时省略它的前缀，保持 {@code cow=10} 这种简短写法。 */
	private static final String VANILLA_NAMESPACE = "minecraft";

	private final int global;
	/** 完整生物 id（如 {@code minecraft:cow}、{@code mymod:foo}）→ 倍率，保持写入顺序。 */
	private final Map<String, Integer> overrides;

	private MobDropSpec(int global, Map<String, Integer> overrides) {
		this.global = global;
		this.overrides = overrides;
	}

	public static MobDropSpec vanilla() {
		return new MobDropSpec(1, new LinkedHashMap<>());
	}

	/** 解析规则值，非法或 null 时退回原版。运行期与命令层共用，避免两处各写一份兜底逻辑。 */
	public static MobDropSpec parseOrVanilla(String raw) {
		return parse(raw).orElseGet(MobDropSpec::vanilla);
	}

	/**
	 * 严格解析：任何一项非法都返回 empty，用于校验器和命令编辑。
	 *
	 * <p>空字符串是合法的，表示全部原版。
	 */
	public static Optional<MobDropSpec> parse(String raw) {
		if (raw == null) {
			return Optional.empty();
		}
		String trimmed = raw.trim();
		if (trimmed.isEmpty()) {
			return Optional.of(vanilla());
		}

		int global = 1;
		Map<String, Integer> overrides = new LinkedHashMap<>();
		for (String entry : trimmed.split(",")) {
			String token = entry.trim();
			if (token.isEmpty()) {
				return Optional.empty();
			}

			int eq = token.indexOf('=');
			if (eq < 0) {
				// 裸数字：全局倍率
				Optional<Integer> value = parseMultiplier(token);
				if (value.isEmpty()) {
					return Optional.empty();
				}
				global = value.get();
				continue;
			}

			if (eq == 0 || eq == token.length() - 1) {
				return Optional.empty();
			}
			String key = token.substring(0, eq).trim();
			Optional<Integer> value = parseMultiplier(token.substring(eq + 1).trim());
			if (value.isEmpty()) {
				return Optional.empty();
			}

			if (isGlobalKey(key)) {
				global = value.get();
			} else {
				Identifier id = resolveEntityId(key);
				if (id == null) {
					return Optional.empty();
				}
				overrides.put(id.toString(), value.get());
			}
		}
		return Optional.of(new MobDropSpec(global, overrides));
	}

	/**
	 * 把 {@code cow} 或 {@code mymod:foo} 解析成注册表里的规范 id，失败返回 null。
	 *
	 * <p>不能用 {@code getValue} 判断存在性：{@code ENTITY_TYPE} 是 {@code DefaultedRegistry}，
	 * 未知 id 会回退到 {@code minecraft:pig} 而不报错，拼写错误会被静默当成猪。
	 * 可靠做法是反查该类型自身的 id 再比对——{@code getValue} 对未知 id 不会返回 null，
	 * 所以这里不需要判空。
	 */
	public static Identifier resolveEntityId(String raw) {
		String candidate = raw.contains(":") ? raw : VANILLA_NAMESPACE + ":" + raw;
		Identifier id = Identifier.tryParse(candidate);
		if (id == null) {
			return null;
		}
		EntityType<?> type = BuiltInRegistries.ENTITY_TYPE.getValue(id);
		Identifier actual = EntityType.getKey(type);
		return id.equals(actual) ? id : null;
	}

	private static Optional<Integer> parseMultiplier(String text) {
		try {
			int value = Integer.parseInt(text);
			return value >= MIN_MULTIPLIER && value <= MAX_MULTIPLIER ? Optional.of(value) : Optional.empty();
		} catch (NumberFormatException e) {
			return Optional.empty();
		}
	}

	private static boolean isGlobalKey(String key) {
		return key.equalsIgnoreCase("all") || key.equalsIgnoreCase("default") || key.equals("*");
	}

	public int global() {
		return global;
	}

	/** 单生物覆盖表（只读视图），key 是完整 id。 */
	public Map<String, Integer> overrides() {
		return Collections.unmodifiableMap(overrides);
	}

	/** 查询某个生物的最终倍率：覆盖优先，其次全局。参数是完整 id。 */
	public int multiplierFor(String mobId) {
		Integer override = overrides.get(mobId);
		return override != null ? override : global;
	}

	public MobDropSpec withGlobal(int multiplier) {
		return new MobDropSpec(multiplier, new LinkedHashMap<>(overrides));
	}

	public MobDropSpec withOverride(String mobId, int multiplier) {
		Map<String, Integer> copy = new LinkedHashMap<>(overrides);
		copy.put(mobId, multiplier);
		return new MobDropSpec(global, copy);
	}

	/**
	 * 移除某个生物的覆盖。
	 *
	 * <p>倍率 1 与「没有覆盖」在全局也是 1 时等价，命令层据此避免留下 {@code cow=1} 这类噪音条目。
	 */
	public MobDropSpec withoutOverride(String mobId) {
		if (!overrides.containsKey(mobId)) {
			return this;
		}
		Map<String, Integer> copy = new LinkedHashMap<>(overrides);
		copy.remove(mobId);
		return new MobDropSpec(global, copy);
	}

	/**
	 * 渲染回规则字符串。
	 *
	 * <p>全局倍率为 1 时省略，避免写出 {@code 1,cow=10} 这种噪音；
	 * 没有任何覆盖且全局为 1 时返回空串（= 全部原版）。
	 * 原版生物省略 {@code minecraft:} 前缀，其它命名空间写全，保证能被 {@link #parse} 读回。
	 */
	public String render() {
		StringBuilder builder = new StringBuilder();
		if (global != 1) {
			builder.append(global);
		}
		for (Map.Entry<String, Integer> entry : overrides.entrySet()) {
			if (builder.length() > 0) {
				builder.append(',');
			}
			builder.append(shortId(entry.getKey())).append('=').append(entry.getValue());
		}
		return builder.toString();
	}

	/** 去掉 {@code minecraft:} 前缀，其它命名空间原样返回。 */
	private static String shortId(String fullId) {
		String prefix = VANILLA_NAMESPACE + ":";
		return fullId.startsWith(prefix) ? fullId.substring(prefix.length()) : fullId;
	}
}
