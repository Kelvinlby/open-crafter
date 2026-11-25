package mod.kelvinlby.opencrafter.client.vision.capture;

import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import mod.kelvinlby.opencrafter.api.frame.FramePublisher;
import mod.kelvinlby.opencrafter.api.frame.FrameSlot;
import mod.kelvinlby.opencrafter.client.vision.VisionStats;
import mod.kelvinlby.opencrafter.client.vision.VisionStream;

import java.nio.ByteBuffer;

/** Copies a completed readback into a shared-memory frame: one memcpy per stream, then commit. */
final class FramePublishing {
	private FramePublishing() {
	}

	static void publish(ReadbackSlot readback, FramePublisher publisher, VisionStats stats) {
		FrameSlot frame = publisher.acquire(readback.specs());
		if (frame == null) {
			stats.onDroppedNoSlot();
			return;
		}
		try {
			writeStreams(readback, frame);
			FrameMeta meta = readback.meta();
			frame.setMeta(meta.toMap());
			frame.commit(meta.frameIndex(), meta.captureNanos());
			stats.onPublished(meta.frameIndex());
		} catch (RuntimeException e) {
			frame.abort();
			throw e;
		}
	}

	private static void writeStreams(ReadbackSlot readback, FrameSlot frame) {
		for (VisionStream stream : readback.streams()) {
			try (GpuBufferSlice.MappedView view = readback.buffer(stream).map(true, false)) {
				ByteBuffer target = frame.stream(stream.wireName);
				target.put(view.data());
			}
		}
	}
}
