package mod.kelvinlby.opencrafter.client.vision.capture;

import com.mojang.blaze3d.systems.RenderSystem;
import mod.kelvinlby.opencrafter.client.vision.compat.ShaderModCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;

/** Reads frame metadata from the render state extracted for the frame that was just rendered. */
final class FrameMetaCollector {
	private FrameMetaCollector() {
	}

	static FrameMeta collect(long frameIndex, long captureNanos, long clientTick, int width, int height, long flowFrom) {
		GameRenderState gameState = Minecraft.getInstance().gameRenderer.gameRenderState();
		LevelRenderState level = gameState.levelRenderState;
		CameraRenderState camera = level.cameraRenderState;
		boolean rendered = gameState.shouldRenderLevel;
		return new FrameMeta(
			frameIndex,
			captureNanos,
			clientTick,
			rendered ? level.gameTime : 0L,
			rendered ? level.worldPartialTicks : 0.0F,
			rendered,
			camera.pos.x(),
			camera.pos.y(),
			camera.pos.z(),
			camera.yRot,
			camera.xRot,
			width,
			height,
			backendName(),
			ShaderModCompat.isShaderPackInUse(),
			flowFrom
		);
	}

	static String backendName() {
		return RenderSystem.getDevice().getDeviceInfo().backendName();
	}
}
