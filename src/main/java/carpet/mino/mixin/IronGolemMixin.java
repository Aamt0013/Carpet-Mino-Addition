package carpet.mino.mixin;

import carpet.mino.settings.MinoSettings;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.golem.IronGolem;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让非玩家建造的铁傀儡不攻击玩家。
 *
 * <p>只拦 {@code canAttack} 一处就够，因为它是目标选择的统一闸门：
 * <ul>
 *   <li>{@code NearestAttackableTargetGoal} 走 {@code TargetingConditions.test}，
 *       内部会调 {@code canAttack}。</li>
 *   <li>{@code DefendVillageTargetGoal} 用的是 {@code forCombat()} 条件，同样会调。</li>
 *   <li>{@code HurtByTargetGoal} 绕过目标筛选直接挑人，但它最终调
 *       {@code Mob.setTarget}，而 {@code setTarget} 会先过 {@code asValidTarget}，
 *       后者同样调 {@code canAttack}，返回 false 就把目标置成 null。</li>
 * </ul>
 *
 * <p>玩家建造的铁傀儡在原版 {@code canAttack} 里就已经不打玩家，这里不重复处理。
 */
@Mixin(IronGolem.class)
public abstract class IronGolemMixin {
	@Inject(method = "canAttack", at = @At("HEAD"), cancellable = true)
	private void mino$sparePlayers(LivingEntity target, CallbackInfoReturnable<Boolean> cir) {
		if (!MinoSettings.friendlyIronGolem) {
			return;
		}
		IronGolem self = (IronGolem) (Object) this;
		if (self.isPlayerCreated()) {
			return;
		}
		if (target instanceof Player) {
			cir.setReturnValue(false);
		}
	}
}
