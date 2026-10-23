package carpet.mino.settings;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

/**
 * 规则值 → 运行期倍率的查询入口。
 *
 * <p>解析逻辑在 {@link MobDropSpec}，这里负责两层缓存：
 * <ol>
 *   <li><b>解析缓存</b>：规则字符串没变就复用上次的 {@link MobDropSpec}，
 *       避免每次生物死亡都重新 split + parseInt。</li>
 *   <li><b>全原版短路</b>：规则为空、或全局为 1 且没有任何覆盖时，
 *       {@link #multiplierFor} 直接返回 1，连 id 都不查。
 *       刷怪塔里绝大多数击杀都落在这一条上。</li>
 * </ol>
 *
 * <p>缓存失效靠比对规则字符串：{@code MinoSettings.mobDropMultiplier} 是普通字段，
 * 规则改动时字符串引用会换掉，{@code equals} 一次就能判断。
 *
 * <p>解析失败（例如玩家手动改了配置文件写了非法值）时按原版处理，不抛异常。
 */
public final class MobDropMultipliers {
	private static String cachedSource = "";
	private static MobDropSpec cachedSpec = MobDropSpec.vanilla();

	/** 缓存值是否为「完全原版」，是则所有查询直接返回 1。默认配置即原版，故初始为 true。 */
	private static boolean cachedVanilla = true;

	private MobDropMultipliers() {
	}

	/**
	 * 查询指定生物类型的倍率。
	 *
	 * @return 1 表示原版（不放大），大于 1 表示倍率
	 */
	public static int multiplierFor(EntityType<?> type) {
		MobDropSpec spec = current();
		if (cachedVanilla) {
			// 没有任何倍率配置：省掉 getKey + toString + 查表。
			return 1;
		}

		Identifier id = EntityType.getKey(type);
		if (id == null) {
			return 1;
		}
		// 用完整 id 查：覆盖表按命名空间区分，只比 path 会把模组生物和原版生物混为一谈。
		return spec.multiplierFor(id.toString());
	}

	private static MobDropSpec current() {
		String source = MinoSettings.mobDropMultiplier;
		if (source == null) {
			source = "";
		}
		// 引用相等先比，避免每次都走字符串内容比较。
		if (source == cachedSource || source.equals(cachedSource)) {
			return cachedSpec;
		}
		cachedSpec = MobDropSpec.parseOrVanilla(source);
		cachedSource = source;
		cachedVanilla = cachedSpec.global() == MobDropSpec.MIN_MULTIPLIER && cachedSpec.overrides().isEmpty();
		return cachedSpec;
	}
}
