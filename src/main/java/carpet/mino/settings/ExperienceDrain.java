package carpet.mino.settings;

import net.minecraft.world.entity.player.Player;

/**
 * 经验修补的安全扣经验工具。
 *
 * <p>原版 {@link Player#giveExperiencePoints(int)} 传负数会走
 * {@code experienceProgress < 0} 的分支：先降级再把进度补回一整级，
 * 若等级已经到 0 则会把 {@code totalExperience} 一并清零。用它对玩家已存的
 * 经验做减法会留下「等级看着没变但总经验被抹掉」的脏数据。
 *
 * <p>这里逐级扣：先吃掉当前等级的进度，再降一级并把进度填到接近满，
 * 直到扣完或等级见底。与 {@code giveExperiencePoints} 的加经验路径互逆，
 * 不会出现进度为负或等级穿底。
 *
 * <p>经验曲线按等级分段线性，反算「从 0 级练到某级需要多少经验」有闭式解，
 * 不必逐级累加——本类被每 tick 调用，逐级循环在 100 级玩家身上会放大成
 * 每秒上千次迭代。
 */
public final class ExperienceDrain {
	private ExperienceDrain() {
	}

	/**
	 * 玩家当前可用于修补的经验总量（等价于「从 0 级练到现在的总经验」）。
	 *
	 * <p>用进度和等级反算，而不是读 {@code totalExperience}：后者在有自定义
	 * 经验条或命令干预时未必与等级/进度一致，反算出来的值一定能被
	 * {@link #drain} 完整扣掉。
	 */
	public static int availableExperience(Player player) {
		int level = player.experienceLevel;
		int total = experienceForLevel(level);
		return total + (int) (player.experienceProgress * experienceNeededForLevel(level));
	}

	/**
	 * 从玩家身上扣掉至多 {@code amount} 点经验，返回实际扣掉的数量。
	 *
	 * <p>逐级回退而不是直接改 {@code totalExperience}：等级、进度、总经验三者
	 * 必须同步，否则经验条显示会和实际值对不上。
	 */
	public static int drain(Player player, int amount) {
		if (amount <= 0) {
			return 0;
		}
		int remaining = amount;

		// 先扣当前等级的进度。
		int progressPoints = (int) (player.experienceProgress * experienceNeededForLevel(player.experienceLevel));
		int takeFromProgress = Math.min(remaining, progressPoints);
		if (takeFromProgress > 0) {
			int needed = experienceNeededForLevel(player.experienceLevel);
			player.experienceProgress -= (float) takeFromProgress / needed;
			if (player.experienceProgress < 0.0F) {
				player.experienceProgress = 0.0F;
			}
			remaining -= takeFromProgress;
		}

		// 进度扣完后再一级一级往下掉。跨度通常只有 1-2 级，不是热点。
		while (remaining > 0 && player.experienceLevel > 0) {
			int needed = experienceNeededForLevel(player.experienceLevel - 1);
			player.experienceLevel--;
			if (remaining >= needed) {
				remaining -= needed;
				player.experienceProgress = 0.0F;
			} else {
				// 这一级只扣一部分，剩下的换算成进度保留。
				player.experienceProgress = (float) (needed - remaining) / needed;
				remaining = 0;
			}
		}

		int drained = amount - remaining;
		player.totalExperience -= drained;
		if (player.totalExperience < 0) {
			player.totalExperience = 0;
		}
		return drained;
	}

	/**
	 * 从 0 级练到 {@code level} 级所需的总经验，闭式解。
	 *
	 * <p>原版曲线（{@code Player.getXpNeededForNextLevel}）：
	 * <pre>
	 * 0-14 级：7 + level * 2
	 * 15-29 级：37 + (level - 15) * 5
	 * 30+ 级：112 + (level - 30) * 9
	 * </pre>
	 * 每段都是等差数列，直接套求和公式。
	 *
	 * <p>返回值封顶在 {@link Integer#MAX_VALUE}：曲线是二次的，约 21864 级时
	 * 总经验就会溢出 int。正常玩法到不了那个等级，但 {@code /xp set} 可以，
	 * 封顶比回绕成负数安全——负数会让「可用经验」判断全部失效。
	 */
	private static int experienceForLevel(int level) {
		if (level <= 0) {
			return 0;
		}
		long total;
		if (level <= 15) {
			// 0..level-1 共 level 项，首项 7、公差 2。
			total = (long) level * 7 + (long) level * (level - 1);
		} else if (level <= 30) {
			int n = level - 15;
			total = 15L * 7 + 15L * 14 + (long) n * 37 + (long) n * (n - 1) * 5 / 2;
		} else {
			int n = level - 30;
			total = 15L * 7 + 15L * 14                       // 0-14 级
					+ 15L * 37 + 15L * 14 * 5 / 2            // 15-29 级
					+ (long) n * 112 + (long) n * (n - 1) * 9 / 2;
		}
		return (int) Math.min(total, Integer.MAX_VALUE);
	}

	/** 第 {@code level} 级升到下一级所需经验，与 {@code Player.getXpNeededForNextLevel} 同公式。 */
	private static int experienceNeededForLevel(int level) {
		if (level >= 30) {
			return 112 + (level - 30) * 9;
		}
		if (level >= 15) {
			return 37 + (level - 15) * 5;
		}
		return 7 + level * 2;
	}
}
