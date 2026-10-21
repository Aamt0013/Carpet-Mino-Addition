package carpet.mino.client.feature.feature_name.mixin;

import carpet.mino.client.feature.feature_name.Feature;
import carpet.mino.CarpetMinoAddition;

import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 客户端功能模组模板 —— Mixin（可选）。
 *
 * 原则：Mixin 只做钩子与转发，业务逻辑全部放在 Feature.java。
 * 这样删除功能时只需要删掉这个文件和 json 里的一行登记，不留编译依赖。
 *
 * TODO(接入) 复制后按顺序处理：
 *   1. 包名 feature_name → 你的功能名；类名 Feature → 大驼峰功能名
 *   2. 移动本文件到 .../feature/<feature_name>/mixin/FeatureMixin.java
 *   3. 在 src/client/resources/carpet-mino-addition.client.mixins.json 的 client 数组中加入
 *      "FeatureMixin"（Mixin 包名与配置里的 package 一致，只写类名）
 *   4. 按需改 @Mixin 目标类与 method 名；处理完全部 TODO 后删除本注释块
 */
@Mixin(Minecraft.class)
public class FeatureMixin {

	/**
	 * 注入客户端每帧主循环开头。
	 *
	 * 1.21.11 中 Minecraft.run() 内部会创建 Runnable 再交给线程执行，
	 * 对 run() 直接 @Inject 在部分启动路径下不会命中。这里示范的写法
	 * 与 ExampleClientMixin 相同（HEAD of run），若实际不生效，
	 * 改用 Fabric 的 ClientTickEvents.END_CLIENT_TICK 事件更稳：
	 *   net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents
	 *         .END_CLIENT_TICK.register(client -> Feature.onClientTick());
	 */
	@Inject(at = @At("HEAD"), method = "run")
	private void feature_name$onClientRun(CallbackInfo info) {
		// 只做转发；Feature.enabled 检查放在 Feature 内部，这里保持最薄。
		Feature.onClientTick();
	}

	// TODO(接入) 需要第二个钩子时，按同样的"转发到 Feature"模式往下加，
	// 不要把逻辑写在这里。方法名前缀 feature_name$ 用于避免与其他模组的
	// injector 方法重名冲突。
}
