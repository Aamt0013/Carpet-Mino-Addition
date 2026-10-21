package carpet.mino.settings.validator;

import carpet.api.settings.CarpetRule;
import carpet.api.settings.Validator;
import net.minecraft.commands.CommandSourceStack;
import org.jspecify.annotations.Nullable;

/**
 * 流浪商人消失延迟的校验器。
 *
 * <p>接受三种值：
 * <ul>
 *   <li>{@code 0} 原版（40 分钟），也是规则的默认值——校验器必须放行默认值，
 *       否则 Carpet 启动时重置配置会抛 {@code Rule couldn't be set to default value}。</li>
 *   <li>{@code -1} 永不消失。</li>
 *   <li>{@code 1-604800} 指定秒数（最多 7 天）。</li>
 * </ul>
 */
public class WanderingTraderDespawnValidator extends Validator<Integer> {
	/** 上限 7 天，再长没有实际意义，也避免玩家误填天文数字。 */
	private static final int MAX_SECONDS = 604800;

	@Override
	public @Nullable Integer validate(CommandSourceStack source, CarpetRule<Integer> rule, Integer newValue, String userInput) {
		if (newValue == null) {
			return null;
		}
		if (newValue == 0 || newValue == -1) {
			return newValue;
		}
		if (newValue < 1 || newValue > MAX_SECONDS) {
			return null;
		}
		return newValue;
	}

	@Override
	public String description() {
		return "0 (vanilla) | -1 (never) | 1-" + MAX_SECONDS + " seconds";
	}
}
