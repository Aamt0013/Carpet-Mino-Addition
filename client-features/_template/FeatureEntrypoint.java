package carpet.mino.client.feature.feature_name;

import carpet.mino.CarpetMinoAddition;

/**
 * 客户端功能模组模板 —— 入口类。
 *
 * 原则：本类是功能的唯一对外入口。Mixin、事件回调都只调用这里的方法，
 * 功能内部状态全部私有，做到"整包摘除不影响其他功能"。
 *
 * TODO(接入) 复制后按顺序处理：
 *   1. 包名 feature_name → 你的功能名（小写 snake_case，例 waypoint_hud）
 *   2. 类名 Feature → 你的功能名（大驼峰，例 WaypointHud）
 *   3. 移动本文件到 src/client/java/com/example/client/feature/<feature_name>/Feature.java
 *   4. 在 src/client/java/com/example/client/CarpetMinoAdditionClient.java 的
 *      onInitializeClient() 中加一行：new Feature().init();
 *   5. 按需启用下面的输入 / 渲染样例分支
 *   6. 处理完全部 TODO 后删除本注释块
 */
public class Feature {

	/** 功能开关：置 false 可整块禁用该功能（保留代码便于回滚）。 */
	public static boolean enabled = true;

	/** 输入句柄样例；未使用输入时可整段删除。 */
	private static net.minecraft.client.KeyMapping toggleKey;

	public void init() {
		if (!enabled) {
			CarpetMinoAddition.LOGGER.info("[feature_name] disabled, skipping init");
			return;
		}

		CarpetMinoAddition.LOGGER.info("[feature_name] initialized");

		// TODO(接入) 可选 —— 输入样例：默认键位 RSHIFT，类别 MISC。
		// registerKeyMapping() 会把键位写进原版按键设置界面，玩家可自行改键。
		// 改键后 KeyMapping 的名称/分类应按需调整（见 FeatureKeybind.java）。
		// toggleKey = net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper
		// 		.registerKeyMapping(new net.minecraft.client.KeyMapping(
		// 				"key.carpet-mino-addition.feature_name.toggle",
		// 				net.minecraft.client.KeyMapping.Category.MISC,
		// 				com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
		// 				org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT,
		// 				"category.carpet-mino-addition.features"));

		// TODO(接入) 可选 —— 渲染样例：在 HUD 左上角画一个角标。
		// HudElementRegistry 是 1.21.11 的新版 HUD API（旧版 HudRenderCallback 已废弃）。
		// net.fabricmc.fabric.api.client.rendering.v1.hud.HudElementRegistry
		// 		.attachElementAfter(VanillaHudElements.CHAT, carpet.mino.CarpetMinoAddition.id("feature_name"), FeatureRenderer::render);
	}

	/** 每帧调用一次，由 Mixin 或事件转发进来；toggleKey 为空时直接返回。 */
	public void onClientTick() {
		if (!enabled || toggleKey == null) {
			return;
		}

		// consumeClick() 每次按下只返回一次 true，长按不会重复触发。
		while (toggleKey.consumeClick()) {
			// TODO(接入) 在这里写按键按下后的行为。
			CarpetMinoAddition.LOGGER.info("[feature_name] toggle key pressed");
		}
	}
}
