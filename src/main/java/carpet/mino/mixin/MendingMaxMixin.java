package carpet.mino.mixin;

import carpet.mino.settings.ExperienceDrain;
import carpet.mino.settings.MinoSettings;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentEffectComponents;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 经验修补 MAX：定期用经验栏自动修补身上所有带经验修补的装备。
 *
 * <p>原版只在拾取经验球时触发，且由 {@code getRandomItemWith} 随机挑**一件**装备来修，
 * 球的经验不够就只能修一半，剩下的经验进了经验栏却不会再回流到装备上。
 *
 * <p>本规则改成主动结算：每隔 {@link #CHECK_INTERVAL} tick 扫一遍装备槽
 * （主手、副手、头盔、胸甲、护腿、靴子），把耐久未满且带经验修补的那几件一次性修满。
 *
 * <p><b>为什么不每 tick 做：</b>一次结算要走 6 次槽位查询 + 6 次附魔组件查找，
 * 多人服务器上按 tick 频率放大后纯属浪费——耐久掉得再快也不可能 0.5 秒就磨光一整件。
 * 节流到 10 tick 后，修补延迟最多半秒，观感无差别，开销降到 1/10。
 *
 * <p>只动经验栏、不碰经验球：经验球的拾取逻辑保持原版，避免和经验球的
 * {@code repairPlayerItems} 抢同一份经验导致重复修补。
 *
 * <p>扣除走 {@link ExperienceDrain} 的逐级回退，不用
 * {@code giveExperiencePoints(负数)}——后者在等级归零时会把
 * {@code totalExperience} 一并清零。
 */
@Mixin(ServerPlayer.class)
public abstract class MendingMaxMixin {
	/** 结算间隔（tick）。10 tick = 0.5 秒。 */
	@Unique
	private static final int CHECK_INTERVAL = 10;

	/** 参与自动修补的槽位：手持 + 护甲四件。静态常量，避免每次调用新建数组。 */
	@Unique
	private static final EquipmentSlot[] MEND_SLOTS = {
			EquipmentSlot.MAINHAND,
			EquipmentSlot.OFFHAND,
			EquipmentSlot.HEAD,
			EquipmentSlot.CHEST,
			EquipmentSlot.LEGS,
			EquipmentSlot.FEET
	};

	/** 上次结算的 tick，按玩家实例分别记录。 */
	@Unique
	private int mino$lastMendTick;

	@Inject(method = "tick", at = @At("HEAD"))
	private void mino$autoMend(CallbackInfo ci) {
		if (!MinoSettings.mendingMax) {
			return;
		}

		ServerPlayer player = (ServerPlayer) (Object) this;

		// 最廉价的排除：没经验就不用往下走。绝大多数 tick 在这里返回。
		if (player.experienceLevel == 0 && player.experienceProgress <= 0.0F) {
			return;
		}

		// 节流：两次结算至少间隔 CHECK_INTERVAL tick。
		int now = player.tickCount;
		if (now - mino$lastMendTick < CHECK_INTERVAL) {
			return;
		}
		mino$lastMendTick = now;

		// 先扫一遍确认真有要修的东西，再算经验——availableExperience 虽然已是闭式解，
		// 但没必要为「身上全是满耐久装备」这种常见情况付这份开销。
		ItemStack[] damaged = mino$damagedMending(player);
		if (damaged == null) {
			return;
		}
		if (ExperienceDrain.availableExperience(player) <= 0) {
			return;
		}

		for (ItemStack stack : damaged) {
			if (stack == null) {
				continue;
			}
			if (!mino$repairOne(player, stack)) {
				// 经验已经见底，剩下的装备这次修不动了。
				return;
			}
		}
	}

	/**
	 * 收集耐久未满且带经验修补的装备；一件都没有时返回 null。
	 *
	 * <p>返回定长数组而不是 List：最多 6 件，避免每次结算分配 ArrayList。
	 */
	@Unique
	private static ItemStack[] mino$damagedMending(ServerPlayer player) {
		ItemStack[] found = null;
		for (int i = 0; i < MEND_SLOTS.length; i++) {
			ItemStack stack = player.getItemBySlot(MEND_SLOTS[i]);
			if (stack.isEmpty() || !stack.isDamaged()) {
				continue;
			}
			if (!EnchantmentHelper.has(stack, EnchantmentEffectComponents.REPAIR_WITH_XP)) {
				continue;
			}
			if (found == null) {
				found = new ItemStack[MEND_SLOTS.length];
			}
			found[i] = stack;
		}
		return found;
	}

	/**
	 * 用经验栏修补单件装备，返回是否还有经验继续修下一件。
	 *
	 * <p>按「修满所需经验」和「当前可用经验」取小值扣，一次到位：
	 * 逐点扣会让每次结算只修 1 点耐久，经验条看着像被慢慢吸走，观感很差。
	 */
	@Unique
	private static boolean mino$repairOne(ServerPlayer player, ItemStack stack) {
		int damage = stack.getDamageValue();
		if (damage <= 0) {
			return true;
		}

		int neededForFull = mino$experienceFor(player, stack, damage);
		if (neededForFull <= 0) {
			return true;
		}

		int available = ExperienceDrain.availableExperience(player);
		if (available <= 0) {
			return false;
		}

		int drained = ExperienceDrain.drain(player, Math.min(neededForFull, available));
		if (drained <= 0) {
			return false;
		}

		int repaired = EnchantmentHelper.modifyDurabilityToRepairFromXp(player.level(), stack, drained);
		int applied = Math.min(repaired, stack.getDamageValue());
		if (applied > 0) {
			stack.setDamageValue(stack.getDamageValue() - applied);
		}
		return ExperienceDrain.availableExperience(player) > 0;
	}

	/**
	 * 修满 {@code damage} 点耐久需要多少经验。
	 *
	 * <p>经验修补的换算是线性的 {@code floor(xp * factor)}，取一个足够大的采样点
	 * 反推比例即可。不能用 1 点经验采样：{@code floor(1 * factor)} 在 factor 小于 1 时
	 * 会得 0，算出「永远修不满」。
	 */
	@Unique
	private static int mino$experienceFor(ServerPlayer player, ItemStack stack, int damage) {
		int repairedBySample = EnchantmentHelper.modifyDurabilityToRepairFromXp(player.level(), stack, damage);
		if (repairedBySample <= 0) {
			return 0;
		}
		// damage 点经验能修 repairedBySample 点耐久，等比反推修满所需经验，+1 补上取整损失。
		return (int) ((long) damage * damage / repairedBySample) + 1;
	}
}
