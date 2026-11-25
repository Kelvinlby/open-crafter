package mod.kelvinlby.opencrafter.client.vision;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import mod.kelvinlby.opencrafter.client.vision.capture.VisionCapture;
import mod.kelvinlby.opencrafter.client.vision.compat.ShaderModCompat;
import mod.kelvinlby.opencrafter.client.vision.flow.FlowRenderer;
import mod.kelvinlby.opencrafter.client.vision.hud.GuiMaskDraw;
import mod.kelvinlby.opencrafter.client.vision.record.MotionRecorder;
import mod.kelvinlby.opencrafter.client.vision.record.RecordPhase;
import mod.kelvinlby.opencrafter.client.vision.record.SubmitKey;
import mod.kelvinlby.opencrafter.client.vision.record.TrackedSubmitList;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.joml.Matrix4fc;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

/**
 * Static entry points for the vision mixins. Mixins stay one-liners and call into here;
 * everything is a cheap no-op unless the current frame is being captured.
 */
public final class VisionHooks {
	private static @Nullable VisionCapture capture;
	private static @Nullable FlowRenderer flow;
	private static final MotionRecorder RECORDER = new MotionRecorder();

	private VisionHooks() {
	}

	static void install(VisionCapture captureInstance, @Nullable FlowRenderer flowInstance) {
		capture = captureInstance;
		flow = flowInstance;
	}

	static MotionRecorder recorder() {
		return RECORDER;
	}

	// ---- frame boundaries ----

	public static void onFrameStart() {
		if (capture != null) {
			capture.onFrameStart();
		}
	}

	public static void onFrameRendered(RenderTarget mainTarget) {
		if (capture != null) {
			capture.onFrameRendered(mainTarget);
		}
	}

	// ---- flow snapshots ----

	private static boolean flowArmed() {
		return flow != null && flow.isArmed();
	}

	public static void onWorldProjection(Matrix4fc projection) {
		if (flowArmed()) {
			flow.onWorldProjection(projection);
		}
	}

	public static void onLevelRendered(RenderTarget main, CameraRenderState camera) {
		if (flowArmed()) {
			flow.onLevelRendered(main, camera);
		}
	}

	public static void onHandProjection(Matrix4fc projection) {
		if (flowArmed()) {
			flow.onHandProjection(projection);
		}
	}

	public static void onHandRendered(RenderTarget main, GpuTextureView handDepth) {
		if (flowArmed()) {
			flow.onHandRendered(main, handDepth);
		}
	}

	/** Shader mods (Iris) draw the hand inside the level pass with their own projection. */
	public static void beforeShaderHand() {
		if (flowArmed() && ShaderModCompat.isShaderPackInUse()) {
			flow.beforeShaderHand(Minecraft.getInstance().gameRenderer.mainRenderTarget());
		}
	}

	public static void afterShaderHand() {
		if (flowArmed()) {
			flow.afterShaderHand(Minecraft.getInstance().gameRenderer.mainRenderTarget());
		}
	}

	public static void onShaderHandProjection(Matrix4fc projection) {
		if (flowArmed() && ShaderModCompat.isShaderPackInUse()) {
			flow.onShaderHandProjection(projection);
		}
	}

	public static VertexConsumer wrapGuiVertices(GuiElementRenderState element, VertexConsumer builder) {
		return flowArmed() ? flow.wrapGuiVertices(element, builder) : builder;
	}

	/** The GUI draws item icons as blits from a re-packed atlas; remember which item each one is. */
	public static void tagItemIcon(GuiElementRenderState blit, Object modelIdentity) {
		if (flowArmed()) {
			flow.tagItemIcon(blit, modelIdentity);
		}
	}

	public static boolean wantsGuiMask() {
		return flowArmed();
	}

	public static void onGuiDraws(RenderTarget main, GpuBufferSlice dynamicTransforms, Supplier<List<GuiMaskDraw>> draws) {
		if (flowArmed()) {
			flow.onGuiDraws(main, dynamicTransforms, draws.get());
		}
	}

	// ---- geometry recording ----

	public static void beginPhase(RecordPhase phase) {
		RECORDER.pushPhase(phase);
	}

	public static void endPhase() {
		RECORDER.popPhase();
	}

	public static void onEntityExtracted(Object renderState, int entityId) {
		RECORDER.onEntityExtracted(renderState, entityId);
	}

	public static void beginEntity(Object renderState) {
		RECORDER.pushOwner(RECORDER.isRecording() ? RECORDER.entityOwner(renderState) : null);
	}

	public static void beginBlockEntity(long blockPos) {
		RECORDER.pushOwner(RECORDER.isRecording() ? new SubmitKey.BlockEntityOwner(blockPos) : null);
	}

	public static void beginHand() {
		RECORDER.pushPhase(RecordPhase.HAND);
		RECORDER.pushOwner(SubmitKey.HAND_OWNER);
	}

	public static void endHand() {
		RECORDER.popOwner();
		RECORDER.popPhase();
	}

	public static void endOwner() {
		RECORDER.popOwner();
	}

	public static void onSubmit(SubmitNode node) {
		if (RECORDER.isRecording()) {
			RECORDER.onSubmit(node);
		}
	}

	public static <T> List<T> trackSubmits(List<T> submits) {
		if (!RECORDER.hasTaggedSubmits()) {
			return submits;
		}
		RECORDER.beginGroup();
		return new TrackedSubmitList<>(submits, RECORDER);
	}

	public static void endSubmits() {
		RECORDER.endPrepare();
	}

	public static VertexConsumer wrapVertexBuilder(VertexConsumer builder, RenderType renderType) {
		return RECORDER.isRecording() ? RECORDER.wrapSubmitBuilder(builder, renderType.primitiveTopology()) : builder;
	}

	public static void beginParticle(Object particle) {
		RECORDER.setCurrentParticle(RECORDER.isRecording() ? particle : null);
	}

	public static void endParticle() {
		RECORDER.setCurrentParticle(null);
	}

	public static void onParticleQuad(Object renderState, Object layer) {
		RECORDER.onParticleQuad(renderState, layer);
	}

	public static VertexConsumer wrapParticleLayer(Object renderState, Object layer, VertexConsumer builder) {
		return RECORDER.isRecording() ? RECORDER.wrapParticleLayer(renderState, layer, builder) : builder;
	}
}
