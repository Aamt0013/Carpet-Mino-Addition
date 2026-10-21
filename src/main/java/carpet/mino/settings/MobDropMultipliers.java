package carpet.mino.settings;

import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;

/**
 * 规则值 → 运行期倍率的查询入口。
 *
 * <p>解析逻辑在 {@link MobDropSpec}，这里只负责缓存：
 * 字符串没变就直接复用上次的解析结果，避免每次生物死亡都重新 split + parseInt。
 * 掉落发生在死亡瞬间，属于低频路径，但刷怪塔批量击杀时仍可能每 tick 多次命中。
 *
 * <p>解析失败（例如玩家手动改了配置文件写了非法值）时按原版处理，不抛异常。
 */
public final class MobDropMultipliers {
	private static String cachedSource = "";
	private static MobDropSpec cachedSpec = MobDropSpec.vanilla();

	private MobDropMultipliers() {
	}

	/**
	 * 查询指定生物类型的倍率。
	 *
	 * @return 1 表示原版（不放大），大于 1 表示倍率
	 */
	public static int multiplierFor(EntityType<?> type) {
		Identifier id = EntityType.getKey(type);
		if (id == null) {
			return 1;
		}
		// 用完整 id 查：覆盖表按命名空间区分，只比 path 会把模组生物和原版生物混为一谈。
		return current().multiplierFor(id.toString());
	}

	private static MobDropSpec current() {
		String source = MinoSettings.mobDropMultiplier;
		if (source == null) {
			source = "";
		}
		if (source.equals(cachedSource)) {
			return cachedSpec;
		}
		cachedSpec = MobDropSpec.parseOrVanilla(source);
		cachedSource = source;
		return cachedSpec;
	}
}
