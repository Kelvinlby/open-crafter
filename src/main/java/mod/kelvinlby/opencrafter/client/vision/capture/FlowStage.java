package mod.kelvinlby.opencrafter.client.vision.capture;

import com.mojang.renderpearl.api.textures.GpuTexture;

/** The optical-flow part of a capture, driven by {@link VisionCapture} on the render thread. */
public interface FlowStage {
	/**
	 * GPU textures holding the published flow streams (top row first), valid until the next frame.
	 *
	 * @param fromFrameIndex index of the frame the flow refers to, or -1 when there was no usable
	 *                       reference (first frame, resize): the flow is then all zero
	 */
	record Output(GpuTexture flow, GpuTexture occluded, long fromFrameIndex) {
	}

	/** Called at the start of every rendered frame; {@code capturing} arms the mid-frame snapshots. */
	void onFrameStart(boolean capturing);

	/** Computes the flow from the reference frame to the frame being captured and makes it the new reference. */
	Output render(long frameIndex, int width, int height);

	/** The armed frame was not captured after all: discard its snapshots, keep the old reference. */
	void onFrameDropped();
}
