package carpet.mino.mixin;

import carpet.mino.settings.MinoSettings;
import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
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
 * <p><b>一、温暖群系融化</b>——{@code aiStep} 里读
 * {@code EnvironmentAttributes.SNOW_GOLEM_MELTS}，为真就
 * {@code hurtServer(onFire, 1.0F)}。这里直接改掉属性的读取结果，让条件恒为 false。
 *
 * <p>为什么不改成「把伤害量换成 0」：{@code hurtServer} 即使收到 0 也照样走完流程，
 * 会设置 {@code invulnerableTime} / {@code hurtTime}，于是雪傀儡每 tick 刷新无敌帧，
 * 玩家反而打不动它，还会每 tick 播一次受伤动画。把条件掐掉则整段跳过，
 * 后面的铺雪逻辑不受影响。
 *
 * <p><b>二、雨水与浸水</b>——{@code LivingEntity.baseTick} 里判断
 * {@code isSensitiveToWater() && isInWaterOrRain()} 后施加 1 点溺水伤害。
 * 雪傀儡的 {@code isSensitiveToWater()} 恒为 true，这里让它返回 false，
 * 语义上就是「这个实体不怕水」，不用去碰所有实体共用的 {@code baseTick}。
 *
 * <p>两条都只影响环境伤害：被玩家或生物打、掉进岩浆、虚空坠落等照常受伤。
 */
@Mixin(SnowGolem.class)
public abstract class SnowGolemMixin {
	@ModifyExpressionValue(
			method = "aiStep",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/attribute/EnvironmentAttributeSystem;getValue(Lnet/minecraft/world/attribute/EnvironmentAttribute;Lnet/minecraft/world/phys/Vec3;)Ljava/lang/Object;"
			)
	)
	private Object mino$neverMelt(Object original) {
		if (MinoSettings.sturdySnowGolem) {
			return Boolean.FALSE;
		}
		return original;
	}

	@Inject(method = "isSensitiveToWater", at = @At("HEAD"), cancellable = true)
	private void mino$ignoreWater(CallbackInfoReturnable<Boolean> cir) {
		if (MinoSettings.sturdySnowGolem) {
			cir.setReturnValue(false);
		}
	}
}
