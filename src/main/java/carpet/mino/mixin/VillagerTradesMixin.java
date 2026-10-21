package carpet.mino.mixin;

import carpet.mino.settings.MinoSettings;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.HolderSet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.RandomSource;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.item.enchantment.ItemEnchantments;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.functions.EnchantWithLevelsFunction;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 村民交易 Pro：工具商、武器匠与盔甲商的钻石装备有小概率带满级附魔组合。
 *
 * <p>26.2 起村民交易完全数据驱动：每笔交易是 {@code data/minecraft/villager_trade/} 下的 JSON，
 * 附魔由其中的 {@code minecraft:enchant_with_levels} 函数施加，等级 5-19（加成后约 6-27 级）。
 * 而效率 V、锋利 V、保护 IV 都需要 30 级以上，所以原版出不了满级。
 * 本 mixin 挂在 {@link EnchantWithLevelsFunction#run} 内部的
 * {@code EnchantmentHelper.enchantItem} 上。
 *
 * <p>该函数不止村民交易在用，还有 11 张宝箱战利品表（末地城、试炼密室、远古城市等），
 * 所以入口处用 {@code THIS_ENTITY} 是否为 {@code AbstractVillager} 过滤，只改村民交易。
 *
 * <p>覆盖范围（都是原版会附魔的钻石物品）：
 * <table border="1">
 *   <tr><th>职业</th><th>等级</th><th>物品</th></tr>
 *   <tr><td>工具商</td><td>4</td><td>钻石斧、钻石铲</td></tr>
 *   <tr><td>工具商</td><td>5</td><td>钻石镐</td></tr>
 *   <tr><td>武器匠</td><td>4</td><td>钻石斧</td></tr>
 *   <tr><td>武器匠</td><td>5</td><td>钻石剑</td></tr>
 *   <tr><td>盔甲商</td><td>4</td><td>钻石护腿、钻石靴子</td></tr>
 *   <tr><td>盔甲商</td><td>5</td><td>钻石头盔、钻石胸甲</td></tr>
 *   <tr><td>制箭师</td><td>4</td><td>弓</td></tr>
 *   <tr><td>制箭师</td><td>5</td><td>弩</td></tr>
 * </table>
 *
 * <p>附魔池按物品能力划分，依据原版 {@code enchantable/*} 标签：
 * <ul>
 *   <li>工具（镐/铲）：效率 V、时运 III、精准采集 I、耐久 III</li>
 *   <li>斧：锋利/亡灵杀手/节肢杀手 V、效率 V、时运 III、精准采集 I、耐久 III</li>
 *   <li>剑：锋利/亡灵杀手/节肢杀手 V、抢夺 III、火焰附加 II、击退 II、横扫之刃 III、耐久 III</li>
 *   <li>弓：力量 V、冲击 II、火矢 I、无限 I、耐久 III</li>
 *   <li>弩：多重射击 I、穿透 IV、快速装填 III、耐久 III</li>
 *   <li>护甲：保护系四选一 IV、耐久 III，另按槽位补头盔的呼吸 III 与水下速掘 I、
 *       靴子的摔落缓冲 IV 与深海探索者 III</li>
 * </ul>
 * 斧的武器类附魔只有锋利系列——抢夺、火焰附加、击退、横扫之刃都只挂在 {@code #minecraft:swords} 上。
 *
 * <p>护甲池不含经验修补、迅捷潜行、灵魂疾行、冰霜行者：它们在 {@code #minecraft:treasure} 里，
 * 而交易附魔来源 {@code #minecraft:on_traded_equipment} 只等于 {@code #minecraft:non_treasure}。
 *
 * <p>护甲池也不含荆棘：荆棘的 {@code supported_items} 覆盖全部护甲但 {@code primary_items} 只有胸甲，
 * 留着它会让胸甲和护腿的组合退化成唯一解。去掉后这两件只会出 2 条附魔，是刻意接受的。
 *
 * <p>互斥交给 {@link Enchantment#areCompatible}：锋利/亡灵杀手/节肢杀手、时运/精准采集、
 * 保护系四选一、冰霜行者/深海探索者。
 *
 * <p>适用性判断用 {@link Enchantment#isSupportedItem} 而不是 {@code isPrimaryItem}：
 * 锋利系的 {@code supported_items} 含 axes，但 {@code primary_items} 只到 melee_weapon（剑与长矛）。
 * {@code isPrimaryItem} 会把斧头误判为「不能附锋利」，而原版斧头确实可以附，只是附魔台滚不出来。
 */
@Mixin(EnchantWithLevelsFunction.class)
public abstract class VillagerTradesMixin {
	/**
	 * 工具商满级概率（万分比，1500 = 15%）。
	 *
	 * <p>工具商能附魔的钻石装备只有 3 件（L4 斧铲、L5 镐），而 {@code updateTrades}
	 * 只生成当前等级那一组，召唤 5 级村民时只有钻石镐 1 件可刷——是三个职业里最少的。
	 * 若与其它职业同概率，平均要召唤 20 次才出一件，所以这里给到最高的单件概率补偿。
	 */
	@Unique
	private static final int TOOL_CHANCE = 1500;
	/** 武器匠满级概率（万分比，1000 = 10%）。5 级只卖钻石剑 1 件。 */
	@Unique
	private static final int WEAPON_CHANCE = 1000;
	/**
	 * 盔甲商满级概率（万分比，800 = 8%）。
	 *
	 * <p>盔甲商 5 级卖头盔与胸甲 2 件，每次召唤掷两次骰，
	 * 所以单件概率可以比工具商低。
	 */
	@Unique
	private static final int ARMOR_CHANCE = 800;
	/**
	 * 制箭师的弓与弩满级概率（万分比，1200 = 12%）。
	 *
	 * <p>两级可刷件数不同：
	 * <ul>
	 *   <li>4 级卖附魔弓，该级 2 个候选抽 2 条，弓必定出现，每次召唤掷 1 次骰，
	 *       实际出货率 12%，平均 8.3 次召唤。</li>
	 *   <li>5 级卖附魔弩，该级 3 个候选抽 2 条，弩只有 2/3 概率被抽中，
	 *       实际出货率 2/3 × 12% = 8%，平均 12.5 次召唤。</li>
	 * </ul>
	 * 弩的附魔池只有 4 条且含一组互斥，满级组合比钻石装备更集中，
	 * 所以单件概率给得比工具商略低。
	 */
	@Unique
	private static final int BOW_CHANCE = 1200;

	/** 至少抽几条满级附魔。 */
	@Unique
	private static final int MIN_PERFECT_COUNT = 2;
	/** 最多抽几条。 */
	@Unique
	private static final int MAX_PERFECT_COUNT = 3;
	/**
	 * 抽到 {@link #MAX_PERFECT_COUNT} 条的概率（百分比）。
	 *
	 * <p>不用 {@code random.nextInt(MAX-MIN+1)} 的均匀分布——那样一半产出只有 2 条，
	 * 满级感偏弱。这里按权重抽：默认 75% 给满 3 条，25% 给 2 条。
	 */
	@Unique
	private static final int MAX_COUNT_WEIGHT = 75;

	/** 剑的满级附魔池。 */
	@Unique
	private static final PoolSpec SWORD_POOL = new PoolSpec(WEAPON_CHANCE, List.of(
			new PerfectEnchantment(Enchantments.SHARPNESS, 5),
			new PerfectEnchantment(Enchantments.SMITE, 5),
			new PerfectEnchantment(Enchantments.BANE_OF_ARTHROPODS, 5),
			new PerfectEnchantment(Enchantments.LOOTING, 3),
			new PerfectEnchantment(Enchantments.FIRE_ASPECT, 2),
			new PerfectEnchantment(Enchantments.KNOCKBACK, 2),
			new PerfectEnchantment(Enchantments.SWEEPING_EDGE, 3),
			new PerfectEnchantment(Enchantments.UNBREAKING, 3)
	));

	/** 斧的满级附魔池：武器类只有锋利系列，另加采集类。 */
	@Unique
	private static final PoolSpec AXE_POOL = new PoolSpec(WEAPON_CHANCE, List.of(
			new PerfectEnchantment(Enchantments.SHARPNESS, 5),
			new PerfectEnchantment(Enchantments.SMITE, 5),
			new PerfectEnchantment(Enchantments.BANE_OF_ARTHROPODS, 5),
			new PerfectEnchantment(Enchantments.EFFICIENCY, 5),
			new PerfectEnchantment(Enchantments.FORTUNE, 3),
			new PerfectEnchantment(Enchantments.SILK_TOUCH, 1),
			new PerfectEnchantment(Enchantments.UNBREAKING, 3)
	));

	/** 镐与铲的满级附魔池。 */
	@Unique
	private static final PoolSpec MINING_TOOL_POOL = new PoolSpec(TOOL_CHANCE, List.of(
			new PerfectEnchantment(Enchantments.EFFICIENCY, 5),
			new PerfectEnchantment(Enchantments.FORTUNE, 3),
			new PerfectEnchantment(Enchantments.SILK_TOUCH, 1),
			new PerfectEnchantment(Enchantments.UNBREAKING, 3)
	));

	/**
	 * 弓的满级附魔池（制箭师 4 级）。
	 *
	 * <p>弓能吃到的附魔只有这 5 条（{@code #minecraft:enchantable/bow}）。
	 * 无限与经验修补互斥（{@code #minecraft:exclusive_set/bow}），
	 * 经验修补不在交易标签里，所以池里只放无限，互斥交给
	 * {@link Enchantment#areCompatible} 处理。
	 */
	@Unique
	private static final PoolSpec BOW_POOL = new PoolSpec(BOW_CHANCE, List.of(
			new PerfectEnchantment(Enchantments.POWER, 5),
			new PerfectEnchantment(Enchantments.PUNCH, 2),
			new PerfectEnchantment(Enchantments.FLAME, 1),
			new PerfectEnchantment(Enchantments.INFINITY, 1),
			new PerfectEnchantment(Enchantments.UNBREAKING, 3)
	));

	/**
	 * 弩的满级附魔池（制箭师 5 级）。
	 *
	 * <p>多重射击与穿透互斥（{@code #minecraft:exclusive_set/crossbow}），
	 * 两条都放进池里，由 {@link Enchantment#areCompatible} 保证只取一条。
	 */
	@Unique
	private static final PoolSpec CROSSBOW_POOL = new PoolSpec(BOW_CHANCE, List.of(
			new PerfectEnchantment(Enchantments.MULTISHOT, 1),
			new PerfectEnchantment(Enchantments.PIERCING, 4),
			new PerfectEnchantment(Enchantments.QUICK_CHARGE, 3),
			new PerfectEnchantment(Enchantments.UNBREAKING, 3)
	));

	/**
	 * 护甲的满级附魔池，四件共用一个。
	 *
	 * <p>槽位专属的几条（呼吸、水下速掘、摔落缓冲、深海探索者）在别的槽位上
	 * 会被 {@link Enchantment#isSupportedItem} 挡掉，不需要拆成四个池。
	 */
	@Unique
	private static final PoolSpec ARMOR_POOL = new PoolSpec(ARMOR_CHANCE, List.of(
			new PerfectEnchantment(Enchantments.PROTECTION, 4),
			new PerfectEnchantment(Enchantments.FIRE_PROTECTION, 4),
			new PerfectEnchantment(Enchantments.BLAST_PROTECTION, 4),
			new PerfectEnchantment(Enchantments.PROJECTILE_PROTECTION, 4),
			new PerfectEnchantment(Enchantments.RESPIRATION, 3),
			new PerfectEnchantment(Enchantments.AQUA_AFFINITY, 1),
			new PerfectEnchantment(Enchantments.FEATHER_FALLING, 4),
			new PerfectEnchantment(Enchantments.DEPTH_STRIDER, 3),
			new PerfectEnchantment(Enchantments.UNBREAKING, 3)
	));

	@WrapOperation(
			method = "run",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/item/enchantment/EnchantmentHelper;enchantItem(Lnet/minecraft/util/RandomSource;Lnet/minecraft/world/item/ItemStack;ILnet/minecraft/core/RegistryAccess;Ljava/util/Optional;)Lnet/minecraft/world/item/ItemStack;"
			)
	)
	private ItemStack mino$maybePerfectEnchants(
			RandomSource random,
			ItemStack stack,
			int level,
			RegistryAccess registryAccess,
			Optional<? extends HolderSet<Enchantment>> allowed,
			Operation<ItemStack> original,
			@Local(argsOnly = true) LootContext lootContext
	) {
		PoolSpec spec = poolFor(stack.getItem());
		// 只对村民交易生效。26.2 里 enchant_with_levels 还被 11 张宝箱表使用
		// （末地城、试炼密室、远古城市等），不拦的话开箱子的钻石装备也会被换成满级。
		// 村民交易的 LootContext 一定带 AbstractVillager 作为 THIS_ENTITY，宝箱则不是。
		if (spec == null || !MinoSettings.villagerTradePro || !mino$isVillagerTrade(lootContext)) {
			return original.call(random, stack, level, registryAccess, allowed);
		}
		// 先掷概率再决定走哪条路：命中时原版那次 enchantItem 的结果会被整个丢弃，
		// 先调用等于白烧一次附魔抽取，还额外推进了世界随机数。
		if (random.nextInt(10000) < spec.chance()) {
			ItemStack perfect = mino$applyPerfectEnchants(stack, registryAccess, random, spec.enchantments());
			if (perfect != null) {
				return perfect;
			}
			// 池里一条都凑不出来：退回原版附魔，避免产出未附魔的物品。
		}
		return original.call(random, stack, level, registryAccess, allowed);
	}

	/** 判断这次附魔是否来自村民交易。 */
	@Unique
	private static boolean mino$isVillagerTrade(LootContext lootContext) {
		Entity source = lootContext.getOptionalParameter(LootContextParams.THIS_ENTITY);
		return source instanceof AbstractVillager;
	}

	/** 按物品选择附魔池；不在覆盖范围内返回 null。 */
	@Unique
	private static PoolSpec poolFor(Item item) {
		if (item == Items.DIAMOND_SWORD) {
			return SWORD_POOL;
		}
		if (item == Items.DIAMOND_AXE) {
			return AXE_POOL;
		}
		if (item == Items.DIAMOND_PICKAXE || item == Items.DIAMOND_SHOVEL) {
			return MINING_TOOL_POOL;
		}
		if (item == Items.DIAMOND_HELMET
				|| item == Items.DIAMOND_CHESTPLATE
				|| item == Items.DIAMOND_LEGGINGS
				|| item == Items.DIAMOND_BOOTS) {
			return ARMOR_POOL;
		}
		if (item == Items.BOW) {
			return BOW_POOL;
		}
		if (item == Items.CROSSBOW) {
			return CROSSBOW_POOL;
		}
		return null;
	}

	/**
	 * 从池里随机抽 2-3 条互不冲突的附魔，替换原版的随机附魔。
	 *
	 * <p>洗牌后按序取，不适用或互斥的跳过，直到凑够目标条数。
	 *
	 * <p>一条都抽不到时返回 null，由调用方退回原版附魔。不能直接返回未附魔的 stack：
	 * 命中概率时已跳过原版 {@code enchantItem}，而未附魔的物品会被交易 JSON 里的
	 * {@code minecraft:filtered} 函数判定为不合格并 {@code discard}，整件装备从交易里消失。
	 */
	@Unique
	private static ItemStack mino$applyPerfectEnchants(
			ItemStack stack,
			RegistryAccess registryAccess,
			RandomSource random,
			List<PerfectEnchantment> sourcePool
	) {
		HolderLookup.RegistryLookup<Enchantment> lookup = registryAccess.lookupOrThrow(Registries.ENCHANTMENT);

		// 用原版的洗牌：它直接吃 RandomSource，不必再套一层 java.util.Random。
		List<PerfectEnchantment> pool = Util.shuffledCopy(sourcePool.toArray(new PerfectEnchantment[0]), random);

		// 加权：MAX_COUNT_WEIGHT% 抽满 MAX 条，其余取 MIN 条。
		int target = random.nextInt(100) < MAX_COUNT_WEIGHT ? MAX_PERFECT_COUNT : MIN_PERFECT_COUNT;
		List<Holder<Enchantment>> chosen = new ArrayList<>();
		List<Integer> chosenLevels = new ArrayList<>();

		for (PerfectEnchantment candidate : pool) {
			if (chosen.size() >= target) {
				break;
			}
			Holder<Enchantment> holder = lookup.get(candidate.key())
					.orElseThrow(() -> new IllegalStateException("Missing enchantment: " + candidate.key()));
			if (!holder.value().isSupportedItem(stack)) {
				continue;
			}
			boolean compatible = true;
			for (Holder<Enchantment> existing : chosen) {
				if (!Enchantment.areCompatible(existing, holder)) {
					compatible = false;
					break;
				}
			}
			if (!compatible) {
				continue;
			}
			chosen.add(holder);
			chosenLevels.add(candidate.level());
		}

		if (chosen.isEmpty()) {
			return null;
		}

		// 用 set 写入空组件而不是 remove：remove 会留下「移除标记」，
		// 序列化后变成 "!minecraft:enchantments": {}，后续 enchant 也无法覆盖。
		stack.set(DataComponents.ENCHANTMENTS, ItemEnchantments.EMPTY);
		for (int i = 0; i < chosen.size(); i++) {
			stack.enchant(chosen.get(i), chosenLevels.get(i));
		}
		return stack;
	}

	/** 满级附魔池的一项：附魔键与它的满级值。 */
	@Unique
	private record PerfectEnchantment(ResourceKey<Enchantment> key, int level) {
	}

	/** 一个物品池：附魔列表 + 触发概率（万分比）。 */
	@Unique
	private record PoolSpec(int chance, List<PerfectEnchantment> enchantments) {
	}
}
