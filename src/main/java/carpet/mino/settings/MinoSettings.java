package carpet.mino.settings;

import carpet.api.settings.Rule;
import carpet.api.settings.RuleCategory;
import carpet.mino.settings.validator.MobDropMultiplierValidator;
import carpet.mino.settings.validator.WanderingTraderDespawnValidator;

/**
 * 本扩展的规则定义。
 *
 * <p>写法与 Carpet 本体一致：public static 字段 + {@link Rule} 注解，字段名就是规则名，
 * 运行期通过 {@code /carpet <规则名> ...} 修改（新版 Carpet 起不再需要 set 关键字）。
 */
public class MinoSettings {
	/** 本扩展的规则类别，规则里通过 {@code categories = {MinoSettings.CATEGORY}} 引用。 */
	public static final String CATEGORY = "MINO";

	/**
	 * 生物掉落倍率。全局默认与单生物覆盖写在同一条规则里，语法见 {@link MobDropSpec}：
	 * <pre>
	 * 3              所有生物 3 倍
	 * cow=10         只有牛 10 倍
	 * 3,cow=10       全局 3 倍，牛单独 10 倍
	 * all=3          全局倍率的显式写法，等价于 3
	 * （留空）        全部原版
	 * </pre>
	 *
	 * <p>倍率 1-1999，1 为原版；生物 id 可省略 {@code minecraft:} 前缀，
	 * 模组生物要写全命名空间（如 {@code mymod:foo=10}）。
	 * 单生物覆盖优先于全局。
	 *
	 * <p>也可以不用记语法：{@code /mino drop <生物> <倍率>} 改单个生物，
	 * {@code /mino drop all <倍率>} 改全局，{@code /mino drop list} 查看，{@code /mino drop clear} 清空。
	 */
	@Rule(categories = {CATEGORY, RuleCategory.SURVIVAL}, strict = false, validators = MobDropMultiplierValidator.class)
	public static String mobDropMultiplier = "";

	/**
	 * 让流浪商人有概率出售冰。
	 *
	 * <p>原版流浪商人的交易表里只有浮冰（{@code minecraft:packed_ice}）和蓝冰
	 * （{@code minecraft:blue_ice}），没有普通冰（{@code minecraft:ice}）。
	 * 开启后把冰作为一种候选加入浮冰所在的池子，和它们一样参与随机抽取，
	 * 出现概率约 12.5%（16 个候选抽 2 条）。
	 *
	 * <p>开启后只对**新生成**的流浪商人生效；已经在世界里的商人要等下一次
	 * {@code updateTrades}（重新加载该区块并刷新交易）才会带上这条。
	 */
	@Rule(categories = {CATEGORY, RuleCategory.SURVIVAL})
	public static boolean wanderingTraderSellsIce = false;

	/**
	 * 村民交易 Pro，包含三部分改动。
	 *
	 * <p><b>一、钻石装备满级附魔</b>：工具商、武器匠、盔甲商卖的钻石装备，
	 * 原版附魔等级约 6-27 级，出不了效率 V、锋利 V 或保护 IV。
	 * 开启后有小概率替换成 2-3 条互不冲突的满级附魔。
	 * 经验修补不会被添加：它不在原版村民交易的附魔标签里。
	 *
	 * <p><b>二、制箭师的弓与弩满级附魔</b>：制箭师 4 级卖附魔弓、5 级卖附魔弩，
	 * 同样是小概率替换成满级组合。
	 *
	 * <p><b>三、农民出售附魔金苹果</b>：农民 5 级原版只卖金胡萝卜与闪烁的西瓜片。
	 * 开启后把附魔金苹果加入该级候选池，64 绿宝石换 1 个，约 6.7% 概率出现。
	 *
	 * <p>其它职业与物品不变。
	 */
	@Rule(categories = {CATEGORY, RuleCategory.SURVIVAL})
	public static boolean villagerTradePro = false;

	/**
	 * 流浪商人的消失延迟（秒）。0 为原版 40 分钟，-1 为永不消失。
	 *
	 * <p>原版在生成商人时硬编码 {@code setDespawnDelay(48000)}（40 分钟），
	 * 到期且玩家没在交易中就 {@code discard()} 掉。想慢慢挑货或回家取绿宝石时
	 * 商人已经走了，是这个机制最烦人的地方。
	 *
	 * <p>设成正数会把延迟改写成该秒数；设成 {@code -1} 则把延迟置 0，
	 * 而 {@code maybeDespawn} 只在 {@code despawnDelay > 0} 时递减，于是永不消失。
	 *
	 * <p>只影响**新生成**的流浪商人。已经在世界里的商人仍按原定延迟消失，
	 * 除非重新生成。
	 */
	@Rule(categories = {CATEGORY, RuleCategory.SURVIVAL}, validators = WanderingTraderDespawnValidator.class)
	public static int wanderingTraderDespawnSeconds = 0;

	/**
	 * 让非玩家建造的铁傀儡不攻击玩家。
	 *
	 * <p>原版铁傀儡的 {@code canAttack} 只挡「玩家建造的 + 玩家」这一种组合，
	 * 村庄自然生成的铁傀儡仍会攻击村庄好感度低的玩家。开启后把这条豁免
	 * 扩展到所有非玩家建造的铁傀儡。
	 *
	 * <p>只改 {@code canAttack} 就够：{@code Mob.setTarget} 会先过
	 * {@code asValidTarget}，其中调 {@code canAttack}，返回 false 则目标置空。
	 * 因此挨打反击（{@code HurtByTargetGoal}）和保卫村庄
	 * （{@code DefendVillageTargetGoal}）这两条绕过目标筛选的路径也会一并失效。
	 *
	 * <p>玩家建造的铁傀儡在原版就已经不打玩家，不受本规则影响。
	 */
	@Rule(categories = {CATEGORY, RuleCategory.SURVIVAL})
	public static boolean friendlyIronGolem = false;

	private MinoSettings() {
	}
}
