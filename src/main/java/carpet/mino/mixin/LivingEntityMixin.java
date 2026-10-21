package carpet.mino.mixin;

import carpet.mino.settings.MinoSettings;
import carpet.mino.settings.MobDropMultipliers;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 生物死亡掉落倍率（含合并保护）。
 *
 * <p>原版链路（26.2）：
 * <pre>
 * LivingEntity.die()
 *   └─ dropAllDeathLoot()
 *        └─ dropFromLootTable(ServerLevel, DamageSource, boolean)              ← 注入点
 *             └─ dropFromLootTable(..., ResourceKey, Consumer&lt;ItemStack&gt;)   ← 合并用的重载
 * </pre>
 *
 * <p>倍率来源：{@link MinoSettings#mobDropMultiplier}，未列出的生物按原版处理。
 *
 * <p>不直接重复调用原方法：原版那一次已经按 {@code spawnAtLocation} 逐个生成了掉落物实体，
 * 倍率 1999 意味着一只生物几千个实体。这里改用带 {@code Consumer} 的重载先攒在临时列表里，
 * 最后按「同物品 + 同组件」合并成若干个不超过最大堆叠数的 ItemStack 再落地。
 * 每一轮仍完整跑一次原版掉落表生成，所以抢夺加成、着火熔炼、稀有掉落概率都逐次生效。
 *
 * <p><b>注入点不能上移（重要）</b>：{@code Mob.dropFromLootTable} 覆写了本方法：
 * <pre>
 * protected void dropFromLootTable(ServerLevel level, DamageSource src, boolean bl) {
 *     super.dropFromLootTable(level, src, bl);   // ← 我们注入的是这里
 *     this.lootTable = Optional.empty();          // ← 随后清空实体自带的掉落表
 * }
 * </pre>
 * 因为 {@code Mob} 先调 {@code super} 再清空，本注入点拿到的 {@code getLootTable()} 仍是
 * 实体自带的那张表；清空动作不受 {@code ci.cancel()} 影响，照常执行。
 * 若上移到 {@code Mob} 那一层或改注入 {@code dropAllDeathLoot}，就必须自己负责清空，
 * 否则实体被反复 tick 时可能重复掉落。
 *
 * <p><b>已知限制</b>：{@code dropFromLootTable} 内部用 {@code getLootTableSeed()}。
 * 普通生物种子为 0，走随机分支，每轮结果不同；但带 {@code DeathLootTableSeed} 的实体
 * （{@code /summon} 指定过、或从存档读回）每轮复用同一种子，N 轮掉落完全一致。
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityMixin {
	@Inject(
			method = "dropFromLootTable(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/world/damagesource/DamageSource;Z)V",
			at = @At("HEAD"),
			cancellable = true
	)
	private void mino$multiplyMobDrops(ServerLevel level, DamageSource damageSource, boolean killedByPlayer, CallbackInfo ci) {
		LivingEntity self = (LivingEntity) (Object) this;

		int multiplier = MobDropMultipliers.multiplierFor(self.getType());
		if (multiplier <= 1) {
			// 1（默认，原版）走原版逻辑；0 及负数不是合法值，一并当作原版处理，保证行为安全。
			return;
		}

		ResourceKey<LootTable> lootTable = self.getLootTable().orElse(null);
		if (lootTable == null) {
			return;
		}

		// 取消原版调用，改由这里接管整份掉落。
		ci.cancel();

		List<ItemStack> collected = new ArrayList<>();
		for (int i = 0; i < multiplier; i++) {
			self.dropFromLootTable(level, damageSource, killedByPlayer, lootTable, collected::add);
		}

		for (ItemStack stack : mino$mergeStacks(collected)) {
			self.spawnAtLocation(level, stack);
		}
	}

	/**
	 * 把同一物品、同一组件的堆叠合并，并按各自的最大堆叠数拆分。
	 *
	 * <p>合并是核心：原版每次 {@code consumer.accept} 只给一个物品，倍率 N 会产生 N 份。
	 * 拆分是合并之后必须补做的：合并后总量必然超过 64，不重新切分就会构造出非法的 count。
	 *
	 * <p>用 {@link LinkedHashMap} 保持首次出现的顺序，掉落顺序与原版一致。
	 */
	@Unique
	private static List<ItemStack> mino$mergeStacks(List<ItemStack> stacks) {
		Map<StackKey, ItemStack> merged = new LinkedHashMap<>();
		for (ItemStack stack : stacks) {
			if (stack.isEmpty()) {
				continue;
			}
			StackKey key = new StackKey(stack);
			ItemStack existing = merged.get(key);
			if (existing == null) {
				merged.put(key, stack.copy());
			} else {
				existing.setCount(existing.getCount() + stack.getCount());
			}
		}

		List<ItemStack> result = new ArrayList<>();
		for (ItemStack stack : merged.values()) {
			int max = stack.getMaxStackSize();
			int remaining = stack.getCount();
			while (remaining > 0) {
				int size = Math.min(remaining, max);
				result.add(stack.copyWithCount(size));
				remaining -= size;
			}
		}
		return result;
	}

	/**
	 * 以「物品 + 组件」作为合并键。
	 *
	 * <p>{@code ItemStack} 本身没有按内容重写 hashCode，不能直接做 Map 的 key；
	 * {@code hashCode} 复用原版 {@link ItemStack#hashItemAndComponents}，与 {@link #equals} 的判据对齐。
	 */
	@Unique
	private record StackKey(ItemStack template) {
		@Override
		public boolean equals(Object other) {
			return other instanceof StackKey key && ItemStack.isSameItemSameComponents(template, key.template);
		}

		@Override
		public int hashCode() {
			return ItemStack.hashItemAndComponents(template);
		}
	}
}
