package carpet.mino.client;

import net.fabricmc.api.ClientModInitializer;

/**
 * 客户端唯一入口。
 *
 * <p>客户端功能全部在 {@code src/client} 下按功能分包，由这里显式注册，
 * 不依赖 classpath 扫描——顺序可控，出问题好定位。
 */
public class CarpetMinoAdditionClient implements ClientModInitializer {
	@Override
	public void onInitializeClient() {
		// no_hunger_drain：飞行时丢弃本地玩家耗食，消除"生存飞行掉饱食度"。
		new carpet.mino.client.feature.no_hunger_drain.NoHungerDrain().init();
	}
}
