package carpet.mino.client.feature.no_hunger_drain;

import carpet.mino.CarpetMinoAddition;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Player;

/**
 * 丢弃本地玩家的食物耗食（exhaustion），默认只在飞行时生效。
 *
 * <p>原理：原版没有"飞行扣饱食度"的机制——{@code Player.causeFoodExhaustion(float)}
 * 的全部调用方只有 HungerMobEffect / ApplyExhaustion / Block.playerDestroy /
 * Minecart.activateMinecart / Player 自身，没有一处与飞行相关。实测感受到的
 * "飞行掉饱食度"最可能来自 {@code FoodData.tick} 的**回血消耗**：饱食度满格且
 * 血量不满时，每 10 tick 回半颗心并扣 {@code min(饱和度, 6.0)} 耗食。
 *
 * <p>做法：Mixin 拦截 {@code Player.causeFoodExhaustion(float)}，在 HEAD 处 cancel。
 * 原版已有的天然豁免是 {@code getAbilities().invulnerable}（无敌则直接 return），
 * 本功能把同类豁免扩展到"本地玩家在飞行"。
 *
 * <p><b>为何闸门用 UUID + 客户端飞行位，而非对象相等 + 服务端飞行位：</b>
 * <ul>
 *   <li>耗食真正写入发生在**集成服务端的 {@code ServerPlayer}**（方法体只在
 *       {@code !level().isClientSide} 时写 foodData），而 {@code Minecraft.getInstance().player}
 *       是 {@code LocalPlayer}——两者是同 UUID 的不同对象。对象引用相等会**恰好放过**
 *       要拦的那次调用，故用 UUID 对齐。</li>
 *   <li>生存飞行（能力伪造类）只改客户端 {@code LocalPlayer.getAbilities().flying}，
 *       服务端 {@code ServerPlayer.flying} 未必同步为 true。所以"在飞"以客户端飞行位
 *       为信号（正是飞行 hack 置位的地方），而不是服务端位。</li>
 * </ul>
 */
public class NoHungerDrain {

	/** 功能开关：置 false 可整块禁用（保留代码便于回滚）。 */
	public static boolean enabled = true;

	/**
	 * 是否"仅在飞行时"丢弃耗食。
	 *
	 * <p>true（默认）：只有本地玩家在飞时才丢弃，走路等其他时候保持原版消耗。
	 * false：本地玩家的耗食**一律**丢弃——因为回血消耗本就与飞行无关，
	 * 这种模式更彻底、且不依赖飞行位检测，想要"永不掉饱食度"就用它。
	 */
	public static boolean requireFlying = true;

	/** 由 CarpetMinoAdditionClient 显式调用一次。功能无按键、无界面。 */
	public void init() {
		if (!enabled) {
			CarpetMinoAddition.LOGGER.info("[no_hunger_drain] disabled, skipping init");
			return;
		}

		CarpetMinoAddition.LOGGER.info("[no_hunger_drain] initialized (requireFlying={})", requireFlying);
	}

	/**
	 * Mixin 唯一查询入口：是否应丢弃这一次耗食。逻辑集中在这里，Mixin 保持最薄。
	 *
	 * <p>注意：本方法会在集成服务端线程被调用，内部读 {@code Minecraft.getInstance().player}
	 * 是一次良性跨线程读（引用与 boolean 原子读），最坏延迟一个 tick 感知飞行状态，
	 * 对本功能可接受。
	 *
	 * @param player 本次耗食的目标实体（即 mixin 的 {@code this}，单机下是 ServerPlayer）
	 */
	public static boolean shouldCancelExhaustion(Player player) {
		if (!enabled) {
			return false;
		}

		LocalPlayer local = Minecraft.getInstance().player;
		if (local == null) {
			return false;
		}

		// 只管自己：单机 / 局域网下 LocalPlayer 与 ServerPlayer 同 UUID，
		// 其他实体（其他玩家、假人）不拦截，避免动到别人。
		if (!local.getUUID().equals(player.getUUID())) {
			return false;
		}

		return !requireFlying || local.getAbilities().flying;
	}
}
