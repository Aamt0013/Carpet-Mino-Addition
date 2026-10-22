package carpet.mino.mixin;

import carpet.mino.settings.MinoSettings;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.animal.golem.SnowGolem;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 让雪傀儡不会因为环境死亡。
 *
 * <p>原版有两个独立的致死点，各处理一处：
 *
 * <p><b>一、热带融化</b>——在 {@code aiStep} 里查 {@code SNOW_GOLEM_MELTS} 环境属性，
 * 为真就 {@code hurtServer(onFire, 1.0F)}。这里把传下去的伤害量改成 0，
 * 而不是整段跳过：后面的铺雪逻辑还要照常跑。
 *
 * <p><b>二、雨水与浸水</b>——{@code LivingEntity.baseTick} 里判断
 * {@code isSensitiveToWater() && isInWaterOrRain()} 后施加 1 点溺水伤害。
 * 雪傀儡的 {@code isSensitiveToWater()} 恒为 true，这里直接让它返回 false，
 * 语义上就是「这个实体不怕水」，不用去碰所有实体共用的 {@code baseTick}。
 *
 * <p>两条都只影响环境伤害：被玩家或生物打、掉进岩浆、虚空坠落等照常受伤。
 */
@Mixin(SnowGolem.class)
public abstract class SnowGolemMixin {
	@WrapOperation(
			method = "aiStep",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/entity/animal/golem/SnowGolem;hurtServer(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;F)Z"
			)
	)
	private boolean mino$skipMeltDamage(SnowGolem self, ServerLevel level, DamageSource source, float amount,
			Operation<Boolean> original) {
		if (!MinoSettings.sturdySnowGolem) {
			return original.call(self, level, source, amount);
		}
		return original.call(self, level, source, 0.0F);
	}

	@Inject(method = "isSensitiveToWater", at = @At("HEAD"), cancellable = true)
	private void mino$ignoreWater(CallbackInfoReturnable<Boolean> cir) {
		if (MinoSettings.sturdySnowGolem) {
			cir.setReturnValue(false);
		}
	}
}
