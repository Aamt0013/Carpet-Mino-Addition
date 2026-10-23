package carpet.mino.mixin;

import carpet.mino.settings.MinoSettings;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import net.minecraft.world.level.block.TripWireHookBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * 恢复刷线机：剪断绊线后不再留下「已剪断」的绊线，而是正常掉线。
 *
 * <p><b>原版现在怎么处理：</b>{@code TripWireBlock.playerWillDestroy} 检测到主手是剪刀时，
 * 不销毁方块，而是 {@code setBlock(DISARMED = true)} 把它留在原地。这是 MC-59471 在
 * 24w33a 的修复。留下的绊线红石信号不再触发，但方块还在。
 *
 * <p><b>为什么改的是绊线钩而不是绊线：</b>绊线钩用 {@code calculateState} 统一推算整条线路
 * 的状态，然后把这批状态写回去。它的循环里对每个相邻方块依次判断「这是不是绊线」，
 * 是就继续处理。剪刀剪断后线路本身没有断，钩子十刻后仍会把 {@code attached} 重新设回
 * {@code true}。只要让这个判断恒为 true，钩子就会无条件地把线路当作绊线继续处理，
 * 于是被剪断的绊线重新进入正常线路流程，剪断即可掉落——这正是 24w33a 之前的行为。
 *
 * <p>注入的是 {@code calculateState} 里第 4 个 {@code BlockState.is(...)}（{@code ordinal = 3}），
 * 即「这一格是绊线吗」那一处。其余三处判断的是绊线钩本身，不能碰。
 *
 * <p>用 {@code @ModifyExpressionValue} 而不是在方法头返回：整个状态推算和邻居通知都还要照常跑，
 * 只有这一个判断被改写。
 */
@Mixin(TripWireHookBlock.class)
public abstract class TripWireBlockMixin {
	@ModifyExpressionValue(
			method = "calculateState",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/level/block/state/BlockState;is(Ljava/lang/Object;)Z",
					ordinal = 3
			)
	)
	private static boolean mino$restoreStringDupe(boolean original) {
		return MinoSettings.stringDupeMino || original;
	}
}
