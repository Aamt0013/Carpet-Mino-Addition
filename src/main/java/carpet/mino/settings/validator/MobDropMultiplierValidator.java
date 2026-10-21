package carpet.mino.settings.validator;

import carpet.api.settings.CarpetRule;
import carpet.api.settings.Validator;
import carpet.mino.settings.MobDropSpec;
import net.minecraft.commands.CommandSourceStack;
import org.jspecify.annotations.Nullable;

/**
 * 生物掉落倍率规则的校验器。
 *
 * <p>语法与解析逻辑都在 {@link MobDropSpec}，这里只负责把解析结果转成 Carpet 的接受/拒绝，
 * 避免校验和运行时各写一套导致「校验通过但跑起来不对」。
 */
public class MobDropMultiplierValidator extends Validator<String> {
	@Override
	public @Nullable String validate(CommandSourceStack source, CarpetRule<String> rule, String newValue, String userInput) {
		if (newValue == null) {
			return null;
		}
		// 归一化后再存，避免 all=3 与 3 两种写法在配置里并存。
		return MobDropSpec.parse(newValue).map(MobDropSpec::render).orElse(null);
	}

	@Override
	public String description() {
		return "multiplier | mob_id=multiplier,... (1-" + MobDropSpec.MAX_MULTIPLIER + ")";
	}
}
