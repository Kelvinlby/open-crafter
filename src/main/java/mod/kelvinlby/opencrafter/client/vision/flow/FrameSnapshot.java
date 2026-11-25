package mod.kelvinlby.opencrafter.client.vision.flow;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import mod.kelvinlby.opencrafter.client.vision.gpu.FullscreenPass;
import mod.kelvinlby.opencrafter.client.vision.gpu.SizedTexture;
import mod.kelvinlby.opencrafter.client.vision.gpu.VisionPipelines;
import mod.kelvinlby.opencrafter.client.vision.gui.GuiFrame;
import mod.kelvinlby.opencrafter.client.vision.record.FrameGeometry;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;

/**
 * Everything flow needs to remember about one captured frame: copies of the world depth (taken
 * before the hand is drawn), the hand depth, the HUD coverage mask, the camera and the
 * recorded moving geometry.
 */
final class FrameSnapshot implements AutoCloseable {
	final SizedTexture worldDepth = new SizedTexture("flow world depth", VisionPipelines.DEPTH_COPY_FORMAT);
	final SizedTexture handDepth = new SizedTexture("flow hand depth", VisionPipelines.DEPTH_COPY_FORMAT);
	/** World depth right before a shader mod draws the hand (only used with shader packs). */
	final SizedTexture preShaderHandDepth = new SizedTexture("flow pre-hand depth", VisionPipelines.DEPTH_COPY_FORMAT);
	final SizedTexture hudMask = new SizedTexture("flow hud mask", VisionPipelines.MASK_FORMAT);
	final CameraSnapshot camera = new CameraSnapshot();
	@Nullable FrameGeometry geometry;
	GuiFrame gui = GuiFrame.EMPTY;
	long frameIndex = -1;
	int width;
	int height;
	boolean handCaptured;
	/** A shader mod drew the hand itself this frame; the vanilla hand pass then draws nothing. */
	boolean shaderHand;
	private boolean begun;

	/** Starts a new capture into this snapshot; textures are (re)allocated lazily. */
	void reset() {
		camera.reset();
		geometry = null;
		gui = GuiFrame.EMPTY;
		frameIndex = -1;
		handCaptured = false;
		shaderHand = false;
		begun = false;
	}

	/** Allocates and clears the textures the first time the frame touches them. */
	void ensureBegun(int width, int height) {
		if (begun) {
			return;
		}
		begun = true;
		this.width = width;
		this.height = height;
		worldDepth.ensureSize(width, height);
		handDepth.ensureSize(width, height);
		hudMask.ensureSize(width, height);
		clear(hudMask, 0.0F);
	}

	void copyWorldDepth(GpuTextureView depth) {
		copyDepth(depth, worldDepth);
	}

	void copyHandDepth(GpuTextureView depth) {
		copyDepth(depth, handDepth);
		handCaptured = true;
	}

	void copyPreShaderHandDepth(GpuTextureView depth) {
		preShaderHandDepth.ensureSize(width, height);
		copyDepth(depth, preShaderHandDepth);
		shaderHand = true;
	}

	/** Hand depth = the depth the shader mod's hand pass changed; "no hand" everywhere else. */
	void captureShaderHandDepth(GpuTextureView depth, boolean zeroToOne) {
		clear(handDepth, camera.clearDepth(zeroToOne));
		FullscreenPass.draw("shader hand depth", VisionPipelines.HAND_DEPTH_DIFF, pass -> {
			pass.setUniform("InSampler", depth, FullscreenPass.nearest());
			pass.setUniform("PreHandDepth", preShaderHandDepth.view(), FullscreenPass.nearest());
		}, handDepth.view());
		handCaptured = true;
	}

	/** Called before flow is computed: textures the frame never wrote get "nothing rendered". */
	void finish(boolean zeroToOne) {
		float clearDepth = camera.clearDepth(zeroToOne);
		if (!camera.levelRendered) {
			clear(worldDepth, clearDepth);
		}
		if (!handCaptured) {
			clear(handDepth, clearDepth);
		}
	}

	private static void copyDepth(GpuTextureView source, SizedTexture target) {
		FullscreenPass.draw("copy depth", VisionPipelines.COPY_DEPTH,
			pass -> pass.setUniform("InSampler", source, FullscreenPass.nearest()),
			target.view());
	}

	private static void clear(SizedTexture texture, float value) {
		RenderSystem.getDevice().createCommandEncoder().clearColorTexture(texture.texture(), new Vector4f(value, 0.0F, 0.0F, 0.0F));
	}

	@Override
	public void close() {
		worldDepth.close();
		handDepth.close();
		preShaderHandDepth.close();
		hudMask.close();
	}
}
