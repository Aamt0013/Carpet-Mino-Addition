package carpet.mino.mixin;

import carpet.mino.settings.MinoSettings;
import net.minecraft.world.entity.npc.wanderingtrader.WanderingTrader;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/**
 * 改写流浪商人的消失延迟。
 *
 * <p>原版 {@code WanderingTraderSpawner} 生成商人后调用
 * {@code setDespawnDelay(48000)}（40 分钟）。{@code maybeDespawn} 每 tick 递减该值，
 * 到 0 且玩家不在交易中时 {@code discard()}。
 *
 * <p>改 {@code setDespawnDelay} 的参数而不是改 {@code maybeDespawn}：前者是唯一的写入入口，
 * 一处拦截就覆盖所有生成路径（自然生成、刷怪蛋、{@code /summon}）。
 * 而且延迟为 0 时 {@code maybeDespawn} 的循环条件不成立，天然就是「永不消失」。
 *
 * <p>只影响设置延迟的那一刻，已经在倒计时的商人不会被追溯修改。
 */
@Mixin(WanderingTrader.class)
public abstract class WanderingTraderDespawnMixin {
	@ModifyVariable(method = "setDespawnDelay", at = @At("HEAD"), argsOnly = true)
	private int mino$overrideDespawnDelay(int ticks) {
		int seconds = MinoSettings.wanderingTraderDespawnSeconds;
		if (seconds == 0) {
			return ticks;
		}
		if (seconds < 0) {
			// 0 让 maybeDespawn 的 `despawnDelay > 0` 不成立，等于永不递减。
			return 0;
		}
		return seconds * 20;
	}
}
