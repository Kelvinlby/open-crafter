package mod.kelvinlby.opencrafter.encoder.client;

import mod.kelvinlby.opencrafter.api.frame.FramePublisher;
import mod.kelvinlby.opencrafter.api.frame.FrameSlot;
import mod.kelvinlby.opencrafter.api.frame.StreamSpec;
import mod.kelvinlby.opencrafter.encoder.output.EpisodeWriter;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Receives the vision sensor's frames in-process (open-crafter entrypoint {@code open-crafter:frames})
 * and hands them to the episode writer. Lossless: the sensor waits rather than drop a frame.
 */
public final class EncoderFrames implements FramePublisher {
	public static final EncoderFrames INSTANCE = new EncoderFrames();

	private volatile @Nullable EpisodeWriter writer;
	private volatile boolean capturing;

	private EncoderFrames() {
	}

	void attach(EpisodeWriter episode) {
		writer = episode;
	}

	/** Whether the frame about to render is captured (settling frames and loading screens are not). */
	void setCapturing(boolean capture) {
		capturing = capture;
	}

	@Override
	public boolean hasReaders() {
		return capturing && writer != null;
	}

	@Override
	public boolean lossless() {
		return true;
	}

	@Override
	public FrameSlot acquire(List<StreamSpec> streams) {
		EpisodeWriter episode = writer;
		if (episode == null) {
			throw new IllegalStateException("No episode to write frames to");
		}
		return new Slot(episode, streams);
	}

	private static final class Slot implements FrameSlot {
		private final EpisodeWriter episode;
		private final Map<String, ByteBuffer> streams = new LinkedHashMap<>();
		private Map<String, Object> meta = Map.of();

		Slot(EpisodeWriter episode, List<StreamSpec> specs) {
			this.episode = episode;
			for (StreamSpec spec : specs) {
				streams.put(spec.name(), episode.borrow(spec.name(), Math.toIntExact(spec.byteSize())));
			}
		}

		@Override
		public ByteBuffer stream(String name) {
			ByteBuffer buffer = streams.get(name);
			if (buffer == null) {
				throw new IllegalArgumentException("No stream " + name + " in this frame");
			}
			return buffer;
		}

		@Override
		public void setMeta(Map<String, Object> frameMeta) {
			meta = frameMeta;
		}

		@Override
		public void commit(long frameIndex, long captureNanos) {
			episode.submit(new EpisodeWriter.Frame(frameIndex, streams, meta));
		}

		@Override
		public void abort() {
			streams.clear();
		}
	}
}
