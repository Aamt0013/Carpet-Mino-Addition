package carpet.mino.mixin;

import carpet.mino.settings.MinoSettings;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderSet;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.npc.villager.AbstractVillager;
import net.minecraft.world.item.trading.TradeSet;
import net.minecraft.world.item.trading.VillagerTrade;
import net.minecraft.world.item.trading.VillagerTrades;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 往原版交易池里追加自定义交易。
 *
 * <p>26.2 的交易是数据驱动的：{@code TradeSet} 从注册表取出候选 {@code HolderSet}，
 * 再由 {@code addOffersFromItemListings} 不放回随机抽取。这里包装 {@code getTrades()}
 * 的返回值，在抽取之前把自定义交易塞进候选集合，让它们和原版商品公平竞争，
 * 而不是额外追加一条必出的交易。
 *
 * <p>覆盖两个池：
 * <ul>
 *   <li><b>流浪商人 uncommon</b>（浮冰/蓝冰所在池，15 候选抽 2 条）：
 *       加 {@code emerald_ice}，受 {@link MinoSettings#wanderingTraderSellsIce} 控制。
 *       候选数 15→16 后冰的出现概率是 1 - C(15,2)/C(16,2) = 12.5%。</li>
 *   <li><b>农民 5 级</b>（金胡萝卜/闪烁西瓜所在池，2 候选抽 2 条）：
 *       加 {@code emerald_enchanted_golden_apple}（附魔金苹果），
 *       受 {@link MinoSettings#villagerTradePro} 控制。
 *       该交易自带 {@code random_chance 0.1} 条件，所以实际出现概率约 6.7%。</li>
 * </ul>
 *
 * <p>池子用「是否含某个标志性原版交易」来识别，比按候选数量判断更稳，
 * 数据包增删条目也不会误判。
 */
@Mixin(AbstractVillager.class)
public abstract class TradePoolMixin {
	/** 冰交易 id（数据文件在 data/carpet-mino-addition/villager_trade/ 下）。 */
	@Unique
	private static final ResourceKey<VillagerTrade> ICE_TRADE_KEY = ResourceKey.create(
			Registries.VILLAGER_TRADE,
			Identifier.fromNamespaceAndPath("carpet-mino-addition", "wandering_trader/emerald_ice")
	);

	/** 附魔金苹果交易 id。 */
	@Unique
	private static final ResourceKey<VillagerTrade> ENCHANTED_GOLDEN_APPLE_TRADE_KEY = ResourceKey.create(
			Registries.VILLAGER_TRADE,
			Identifier.fromNamespaceAndPath("carpet-mino-addition", "farmer/5/emerald_enchanted_golden_apple")
	);

	@WrapOperation(
			method = "addOffersFromTradeSet",
			at = @At(
					value = "INVOKE",
					target = "Lnet/minecraft/world/item/trading/TradeSet;getTrades()Lnet/minecraft/core/HolderSet;"
			)
	)
	private HolderSet<VillagerTrade> mino$injectTrades(TradeSet tradeSet, Operation<HolderSet<VillagerTrade>> original) {
		HolderSet<VillagerTrade> trades = original.call(tradeSet);

		// 先判断池子，再查规则，最后才碰注册表。
		ResourceKey<VillagerTrade> toAdd;
		if (mino$contains(trades, VillagerTrades.WANDERING_TRADER_EMERALD_PACKED_ICE)) {
			if (!MinoSettings.wanderingTraderSellsIce) {
				return trades;
			}
			toAdd = ICE_TRADE_KEY;
		} else if (mino$contains(trades, VillagerTrades.FARMER_5_EMERALD_GOLDEN_CARROT)) {
			if (!MinoSettings.villagerTradePro) {
				return trades;
			}
			toAdd = ENCHANTED_GOLDEN_APPLE_TRADE_KEY;
		} else {
			return trades;
		}

		AbstractVillager self = (AbstractVillager) (Object) this;
		if (!(self.level() instanceof ServerLevel serverLevel)) {
			return trades;
		}

		Registry<VillagerTrade> registry = serverLevel.registryAccess().lookupOrThrow(Registries.VILLAGER_TRADE);
		Optional<Holder.Reference<VillagerTrade>> extra = registry.get(toAdd);
		if (extra.isEmpty()) {
			return trades;
		}

		List<Holder<VillagerTrade>> combined = new ArrayList<>(trades.size() + 1);
		trades.forEach(combined::add);
		combined.add(extra.get());
		return HolderSet.direct(combined);
	}

	/** 池子里是否含指定交易，用于识别是哪个池。 */
	@Unique
	private static boolean mino$contains(HolderSet<VillagerTrade> trades, ResourceKey<VillagerTrade> key) {
		for (Holder<VillagerTrade> holder : trades) {
			if (holder.unwrapKey().map(key::equals).orElse(false)) {
				return true;
			}
		}
		return false;
	}
}
