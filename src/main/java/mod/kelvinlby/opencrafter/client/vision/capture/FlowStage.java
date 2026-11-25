package mod.kelvinlby.opencrafter.client.vision.capture;

import com.mojang.renderpearl.api.textures.GpuTexture;

/** The optical-flow part of a capture, driven by {@link VisionCapture} on the render thread. */
public interface FlowStage {
	/** GPU textures holding the published flow streams (top row first), valid until the next frame. */
	record Output(GpuTexture flow, GpuTexture occluded) {
	}

	/** Called at the start of every rendered frame; {@code capturing} arms the mid-frame snapshots. */
	void onFrameStart(boolean capturing);

	/** Index of the published frame the next flow will refer to, or -1 when there is none yet. */
	long referenceFrameIndex();

	/** Computes the flow from the reference frame to the frame being captured and makes it the new reference. */
	Output render(long frameIndex, int width, int height);

	/** The armed frame was not captured after all: discard its snapshots, keep the old reference. */
	void onFrameDropped();

	FlowStage UNSUPPORTED = new FlowStage() {
		@Override
		public void onFrameStart(boolean capturing) {
		}

		@Override
		public long referenceFrameIndex() {
			return -1L;
		}

		@Override
		public Output render(long frameIndex, int width, int height) {
			throw new UnsupportedOperationException("Optical flow is not available");
		}

		@Override
		public void onFrameDropped() {
		}
	};
}
