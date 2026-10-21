package carpet.mino;

import carpet.CarpetServer;
import net.fabricmc.api.ModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Carpet Mino Addition 主入口。
 *
 * <p>本模组是 Carpet Extension：真正干活的是 {@link MinoExtension}，
 * 这里只负责把扩展注册给 Carpet（{@link CarpetServer#manageExtension}）。
 * 注册顺序必须早于 Carpet 初始化设置管理器，所以放在 Fabric 的 main 入口里。
 */
public class CarpetMinoAddition implements ModInitializer {
	public static final String MOD_ID = "carpet-mino-addition";

	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	/** Carpet 扩展实例。Carpet 会在启动过程中回调它的生命周期方法。 */
	public static final MinoExtension EXTENSION = new MinoExtension();

	@Override
	public void onInitialize() {
		CarpetServer.manageExtension(EXTENSION);
		LOGGER.info("Carpet Mino Addition loaded");
	}
}
