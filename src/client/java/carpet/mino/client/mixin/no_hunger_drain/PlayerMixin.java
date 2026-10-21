package carpet.mino.client.mixin.no_hunger_drain;

import carpet.mino.client.feature.no_hunger_drain.NoHungerDrain;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 拦截 {@code Player.causeFoodExhaustion(float)}，把是否丢弃的判断转发给
 * {@link NoHungerDrain}，本类只做钩子、不写逻辑。
 *
 * <p>登记：{@code carpet-mino-addition.client.mixins.json} 的 {@code client}
 * 数组含 {@code "no_hunger_drain.PlayerMixin"}。条目按
 * {@code package + "." + 条目} 解析（package = {@code carpet.mino.client.mixin}），
 * 故本类必须落在该 package 树内，不能放 feature 包。
 *
 * <p>目标方法是 {@code Player.causeFoodExhaustion(float)}，方法体先查
 * {@code getAbilities().invulnerable} 再写 foodData；
 * 我们在 HEAD 处 cancel，语义等同原版无敌豁免。
 *
 * <p>{@code @Mixin(Player.class)} 对继承该方法的 {@code LocalPlayer}、
 * {@code ServerPlayer} 都生效；谁的耗食该丢、谁不该丢，由
 * {@code NoHungerDrain.shouldCancelExhaustion} 按 UUID + 飞行位判定。
 */
@Mixin(Player.class)
public class PlayerMixin {

	@Inject(method = "causeFoodExhaustion", at = @At("HEAD"), cancellable = true)
	private void no_hunger_drain$cancelExhaustion(float exhaustion, CallbackInfo ci) {
		if (NoHungerDrain.shouldCancelExhaustion((Player) (Object) this)) {
			ci.cancel();
		}
	}
}
