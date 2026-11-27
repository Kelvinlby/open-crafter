package mod.kelvinlby.opencrafter.client.vision.capture;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import mod.kelvinlby.opencrafter.OpenCrafter;
import mod.kelvinlby.opencrafter.api.frame.FrameAnnotators;
import mod.kelvinlby.opencrafter.api.frame.FramePublisher;
import mod.kelvinlby.opencrafter.client.vision.VisionSettings;
import mod.kelvinlby.opencrafter.client.vision.VisionStats;
import mod.kelvinlby.opencrafter.client.vision.VisionStream;
import mod.kelvinlby.opencrafter.client.vision.gpu.RgbCapturePass;
import mod.kelvinlby.opencrafter.client.vision.rate.FramePacer;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/**
 * Render-thread orchestrator of the vision sensor: decides which frames to capture, issues
 * the GPU work for them and publishes completed readbacks. Never blocks on the GPU.
 */
public final class VisionCapture {
	private static final int READBACK_SLOTS = 3;

	private final VisionSettings settings;
	private final FramePublisher publisher;
	private final VisionStats stats;
	private final FramePacer pacer = new FramePacer();
	private final ReadbackPool readbacks = new ReadbackPool(READBACK_SLOTS);
	private final RgbCapturePass rgbPass = new RgbCapturePass();
	private final FlowStage flow;
	private final FrameAnnotators annotators;
	private long clientTicks;
	private long nextFrameIndex;
	private @Nullable CaptureRequest request;

	public VisionCapture(VisionSettings settings, FramePublisher publisher, VisionStats stats, FlowStage flow, FrameAnnotators annotators) {
		this.settings = settings;
		this.publisher = publisher;
		this.stats = stats;
		this.flow = flow;
		this.annotators = annotators;
	}

	public void onClientTick() {
		clientTicks++;
	}

	/** Flow is wanted and someone reads the frames: moving objects are being tracked across captured frames. */
	public boolean tracksMotion() {
		VisionSettings.Snapshot snapshot = settings.get();
		return snapshot.wantsFlow() && publisher.hasReaders();
	}

	/** Start of {@code Minecraft.renderFrame}: decide whether this frame will be captured. */
	public void onFrameStart() {
		request = decideCapture();
		flow.onFrameStart(request != null && request.settings().wantsFlow());
	}

	/** After {@code GameRenderer.render()}: the main target now holds the final presented image. */
	public void onFrameRendered(RenderTarget mainTarget) {
		readbacks.drainReady(slot -> FramePublishing.publish(slot, publisher, stats));
		CaptureRequest current = request;
		request = null;
		if (current != null) {
			captureSafely(current, mainTarget);
		}
	}

	private @Nullable CaptureRequest decideCapture() {
		VisionSettings.Snapshot snapshot = settings.get();
		if (snapshot.streams().isEmpty() || !publisher.hasReaders() || !Minecraft.getInstance().isGameLoadFinished()) {
			return null;
		}
		long now = Util.getNanos();
		if (!pacer.isDue(snapshot.rateMode(), snapshot.fixedHz(), clientTicks, now)) {
			return null;
		}
		pacer.markCaptured(snapshot.fixedHz(), clientTicks, now);
		return new CaptureRequest(snapshot, now, clientTicks);
	}

	private void captureSafely(CaptureRequest current, RenderTarget mainTarget) {
		try {
			capture(current, mainTarget);
		} catch (RuntimeException e) {
			OpenCrafter.LOGGER.error("Vision capture failed; frame skipped", e);
		}
	}

	private void capture(CaptureRequest current, RenderTarget mainTarget) {
		GpuTextureView color = mainTarget.getColorTextureView();
		int width = mainTarget.width;
		int height = mainTarget.height;
		ReadbackSlot slot = color == null ? null : acquireReadback(current.settings(), width, height);
		if (slot == null) {
			stats.onDroppedNoReadback();
			flow.onFrameDropped();
			return;
		}
		long frameIndex = nextFrameIndex++;
		FlowStage.Output flowOutput = current.settings().wantsFlow() ? flow.render(frameIndex, width, height) : null;
		long flowFrom = flowOutput == null ? -1L : flowOutput.fromFrameIndex();
		slot.begin(FrameMetaCollector.collect(frameIndex, current.nanos(), current.clientTick(), width, height, flowFrom, annotators));
		if (current.settings().wants(VisionStream.RGB)) {
			slot.copy(VisionStream.RGB, rgbPass.run(color, width, height));
		}
		if (flowOutput != null) {
			copyFlow(current.settings(), slot, flowOutput);
		}
		stats.onCaptured(width, height);
		stats.setBackend(slot.meta().backend());
	}

	/** A free readback slot; a lossless consumer waits for the GPU rather than losing the frame. */
	private @Nullable ReadbackSlot acquireReadback(VisionSettings.Snapshot snapshot, int width, int height) {
		ReadbackSlot slot = readbacks.acquire(snapshot.streams(), width, height);
		if (slot == null && publisher.lossless()) {
			readbacks.awaitInFlight();
			readbacks.drainReady(ready -> FramePublishing.publish(ready, publisher, stats));
			slot = readbacks.acquire(snapshot.streams(), width, height);
		}
		return slot;
	}

	private static void copyFlow(VisionSettings.Snapshot snapshot, ReadbackSlot slot, FlowStage.Output output) {
		copyIfWanted(snapshot, slot, VisionStream.FLOW, output.flow());
		copyIfWanted(snapshot, slot, VisionStream.FLOW_OCCLUDED, output.occluded());
	}

	private static void copyIfWanted(VisionSettings.Snapshot snapshot, ReadbackSlot slot, VisionStream stream, GpuTexture texture) {
		if (snapshot.wants(stream)) {
			slot.copy(stream, texture);
		}
	}

	private record CaptureRequest(VisionSettings.Snapshot settings, long nanos, long clientTick) {
	}
}
