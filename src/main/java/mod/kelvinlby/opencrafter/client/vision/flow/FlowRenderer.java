package mod.kelvinlby.opencrafter.client.vision.flow;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import mod.kelvinlby.opencrafter.client.vision.capture.FlowStage;
import com.mojang.blaze3d.vertex.VertexConsumer;
import mod.kelvinlby.opencrafter.client.vision.gpu.FullscreenPass;
import mod.kelvinlby.opencrafter.client.vision.gui.GuiElementRecorder;
import mod.kelvinlby.opencrafter.client.vision.gui.GuiFlowRenderer;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import mod.kelvinlby.opencrafter.client.vision.gpu.SizedTexture;
import mod.kelvinlby.opencrafter.client.vision.gpu.VisionPipelines;
import mod.kelvinlby.opencrafter.client.vision.hud.GuiMaskDraw;
import mod.kelvinlby.opencrafter.client.vision.hud.GuiMaskRenderer;
import mod.kelvinlby.opencrafter.client.vision.record.MotionRecorder;
import mod.kelvinlby.opencrafter.client.vision.record.RecordPhase;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

import java.util.List;
import java.util.Optional;

/**
 * Ground-truth optical flow between consecutive captured frames, computed on the GPU:
 * <ol>
 *   <li>static pass: reproject the reference world depth with both cameras (terrain, sky);</li>
 *   <li>motion passes: rasterise recorded moving geometry (world, then hand) where it was in the
 *       reference frame and project its current position;</li>
 *   <li>GUI: elements of the menus/HUD that moved, vanished or appeared (see {@link GuiFlowRenderer});</li>
 *   <li>resolve: moving GUI elements get their motion, other HUD pixels zero flow; rows are
 *       flipped to top-first, y points down.</li>
 * </ol>
 * Occlusion compares each surface point's position in the current frame against the current
 * depth, hand and HUD snapshots.
 */
public final class FlowRenderer implements FlowStage, AutoCloseable {
	private final MotionRecorder recorder;
	private final GuiMaskRenderer guiMask = new GuiMaskRenderer();
	private FrameSnapshot previous = new FrameSnapshot();
	private FrameSnapshot current = new FrameSnapshot();
	private final SizedTexture work = new SizedTexture("flow work", VisionPipelines.FLOW_WORK_FORMAT);
	private final SizedTexture flowOut = new SizedTexture("flow", VisionPipelines.FLOW_FORMAT);
	private final SizedTexture occludedOut = new SizedTexture("flow occluded", VisionPipelines.OCCLUDED_FORMAT);
	private final MotionMesh worldMesh = new MotionMesh("flow world motion mesh");
	private final MotionMesh handMesh = new MotionMesh("flow hand motion mesh");
	private final GuiElementRecorder guiRecorder = new GuiElementRecorder();
	private final GuiFlowRenderer guiFlow = new GuiFlowRenderer();
	private FlowUniforms uniforms;
	private boolean armed;

	public FlowRenderer(MotionRecorder recorder) {
		this.recorder = recorder;
	}

	public boolean isArmed() {
		return armed;
	}

	// ---- FlowStage ----

	@Override
	public void onFrameStart(boolean capturing) {
		armed = capturing;
		recorder.beginFrame(capturing);
		guiRecorder.beginFrame(capturing);
		if (capturing) {
			current.reset();
		}
	}

	@Override
	public Output render(long frameIndex, int width, int height) {
		armed = false;
		current.ensureBegun(width, height);
		current.geometry = recorder.finishFrame();
		current.gui = guiRecorder.finishFrame(width, height);
		current.finish(zeroToOne());
		flowOut.ensureSize(width, height);
		occludedOut.ensureSize(width, height);
		boolean hasReference = hasReference(width, height);
		long from = hasReference ? previous.frameIndex : -1L;
		if (hasReference) {
			computeFlow(width, height);
		} else {
			clearOutputs();
		}
		current.frameIndex = frameIndex;
		swapSnapshots();
		return new Output(flowOut.texture(), occludedOut.texture(), from);
	}

	@Override
	public void onFrameDropped() {
		armed = false;
		recorder.finishFrame();
		guiRecorder.finishFrame(0, 0);
	}

	/** Records the GUI element whose vertices {@code builder} is about to receive. */
	public VertexConsumer wrapGuiVertices(GuiElementRenderState element, VertexConsumer builder) {
		return guiRecorder.wrap(element, builder);
	}

	public void tagItemIcon(GuiElementRenderState blit, Object modelIdentity) {
		guiRecorder.tagItemIcon(blit, modelIdentity);
	}

	// ---- mid-frame snapshots (render thread, armed frames only) ----

	public void onWorldProjection(Matrix4fc projection) {
		current.camera.worldProjection.set(projection);
	}

	/** Vanilla hand projection; ignored when a shader mod drew the hand with its own. */
	public void onHandProjection(Matrix4fc projection) {
		if (!current.shaderHand) {
			current.camera.handProjection.set(projection);
		}
	}

	public void onShaderHandProjection(Matrix4fc projection) {
		current.camera.handProjection.set(projection);
	}

	/** A shader mod is about to draw the first-person hand into the main depth. */
	public void beforeShaderHand(RenderTarget main) {
		GpuTextureView depth = main.getDepthTextureView();
		if (depth != null) {
			current.ensureBegun(main.width, main.height);
			current.copyPreShaderHandDepth(depth);
		}
	}

	public void afterShaderHand(RenderTarget main) {
		GpuTextureView depth = main.getDepthTextureView();
		if (depth != null && current.shaderHand) {
			current.captureShaderHandDepth(depth, zeroToOne());
		}
	}

	public void onLevelRendered(RenderTarget main, CameraRenderState camera) {
		GpuTextureView depth = main.getDepthTextureView();
		if (depth == null) {
			return;
		}
		current.ensureBegun(main.width, main.height);
		current.copyWorldDepth(depth);
		current.camera.viewRotation.set(camera.viewRotationMatrix);
		current.camera.x = camera.pos.x();
		current.camera.y = camera.pos.y();
		current.camera.z = camera.pos.z();
		current.camera.levelRendered = true;
	}

	/** Vanilla hand depth; with a shader mod the vanilla pass is empty and the shader hand is kept. */
	public void onHandRendered(RenderTarget main, GpuTextureView handDepth) {
		if (current.shaderHand) {
			return;
		}
		current.ensureBegun(main.width, main.height);
		current.copyHandDepth(handDepth);
	}

	public void onGuiDraws(RenderTarget main, GpuBufferSlice dynamicTransforms, List<GuiMaskDraw> draws) {
		current.ensureBegun(main.width, main.height);
		guiMask.replay(current.hudMask.view(), dynamicTransforms, draws);
	}

	// ---- flow computation ----

	private boolean hasReference(int width, int height) {
		return previous.frameIndex >= 0 && previous.width == width && previous.height == height;
	}

	private void computeFlow(int width, int height) {
		uniforms().upload(previous.camera, current.camera, width, height, zeroToOne());
		work.ensureSize(width, height);
		drawStatic();
		if (previous.geometry != null) {
			drawMotion(RecordPhase.WORLD, worldMesh, VisionPipelines.FLOW_MOTION_WORLD, previous.worldDepth);
			drawMotion(RecordPhase.HAND, handMesh, VisionPipelines.FLOW_MOTION_HAND, previous.handDepth);
		}
		guiFlow.render(previous.gui, current.gui, width, height);
		resolve();
	}

	private void drawStatic() {
		FullscreenPass.draw("flow static", VisionPipelines.FLOW_STATIC, pass -> {
			bindFlowUniforms(pass);
			pass.setUniform("PrevWorldDepth", previous.worldDepth.view(), FullscreenPass.nearest());
			pass.setUniform("PrevHandDepth", previous.handDepth.view(), FullscreenPass.nearest());
		}, work.view());
	}

	private void drawMotion(RecordPhase phase, MotionMesh mesh, RenderPipeline pipeline, SizedTexture prevDepth) {
		mesh.upload(MotionMeshBuilder.build(previous.geometry, current.geometry, phase));
		if (mesh.vertexCount() == 0) {
			return;
		}
		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
				.createRenderPass(() -> "open-crafter flow motion " + phase, work.view(), Optional.empty())) {
			pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
			bindFlowUniforms(pass);
			pass.setUniform("PrevDepth", prevDepth.view(), FullscreenPass.nearest());
			pass.setVertexBuffer(0, mesh.slice());
			pass.draw(mesh.vertexCount(), 1, 0, 0);
		}
	}

	private void resolve() {
		FullscreenPass.draw("flow resolve", VisionPipelines.FLOW_RESOLVE, pass -> {
			pass.setUniform("FlowWork", work.view(), FullscreenPass.nearest());
			pass.setUniform("PrevHudMask", previous.hudMask.view(), FullscreenPass.nearest());
			pass.setUniform("GuiFlow", guiFlow.flowView(), FullscreenPass.nearest());
			pass.setUniform("CurGuiCover", guiFlow.coverView(), FullscreenPass.nearest());
		}, flowOut.view(), occludedOut.view());
	}

	/** Uniforms shared by the static and motion passes: the block plus the current-frame snapshots. */
	private void bindFlowUniforms(RenderPass pass) {
		pass.setUniform(FlowUniforms.NAME, uniforms().buffer());
		pass.setUniform("CurWorldDepth", current.worldDepth.view(), FullscreenPass.nearest());
		pass.setUniform("CurHandDepth", current.handDepth.view(), FullscreenPass.nearest());
	}

	private void clearOutputs() {
		Vector4f zero = new Vector4f();
		RenderSystem.getDevice().createCommandEncoder().clearColorTexture(flowOut.texture(), zero);
		RenderSystem.getDevice().createCommandEncoder().clearColorTexture(occludedOut.texture(), zero);
	}

	private void swapSnapshots() {
		FrameSnapshot reference = current;
		current = previous;
		previous = reference;
	}

	private FlowUniforms uniforms() {
		if (uniforms == null) {
			uniforms = new FlowUniforms();
		}
		return uniforms;
	}

	private static boolean zeroToOne() {
		return RenderSystem.getDevice().getDeviceInfo().isZZeroToOne();
	}

	@Override
	public void close() {
		previous.close();
		current.close();
		work.close();
		flowOut.close();
		occludedOut.close();
		worldMesh.close();
		handMesh.close();
		guiFlow.close();
		if (uniforms != null) {
			uniforms.close();
		}
	}
}
