package mod.kelvinlby.opencrafter.client.vision.debug;

import mod.kelvinlby.opencrafter.OpenCrafter;
import mod.kelvinlby.opencrafter.api.frame.FramePublisher;
import mod.kelvinlby.opencrafter.api.frame.FrameSlot;
import mod.kelvinlby.opencrafter.api.frame.StreamSpec;
import net.fabricmc.loader.api.FabricLoader;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Debug-only {@link FramePublisher} that writes frames to disk instead of shared memory.
 *
 * <p>Enabled with {@code -Dopencrafter.vision.dump=N} (number of frames to write, after
 * skipping {@code -Dopencrafter.vision.dumpSkip=S} frames, default 40). Files go to
 * {@code <run dir>/open-crafter-dump/}: {@code <index>_rgb.png}, {@code <index>_flow.npy},
 * {@code <index>_flow_occluded.png} and {@code <index>_meta.txt}. Disk I/O runs on a
 * background thread; the slot memory is a heap copy so the render thread never waits.
 */
public final class DebugDumpPublisher implements FramePublisher {
	private static final String COUNT_PROPERTY = "opencrafter.vision.dump";
	private static final String SKIP_PROPERTY = "opencrafter.vision.dumpSkip";

	private final Path directory;
	private final int skip;
	private final int limit;
	private final AtomicInteger committed = new AtomicInteger();
	private final ExecutorService writer = Executors.newSingleThreadExecutor(runnable -> {
		Thread thread = new Thread(runnable, "open-crafter vision dump");
		thread.setDaemon(true);
		return thread;
	});

	private DebugDumpPublisher(Path directory, int skip, int limit) {
		this.directory = directory;
		this.skip = skip;
		this.limit = limit;
	}

	/** Returns the dump publisher when the system property is set, otherwise {@code fallback}. */
	public static FramePublisher wrapIfRequested(FramePublisher fallback) {
		String count = System.getProperty(COUNT_PROPERTY);
		if (count == null) {
			return fallback;
		}
		Path dir = FabricLoader.getInstance().getGameDir().resolve("open-crafter-dump");
		OpenCrafter.LOGGER.warn("Vision debug dump enabled: frames go to {} instead of the backend", dir);
		return new DebugDumpPublisher(dir, Integer.getInteger(SKIP_PROPERTY, 40), Integer.parseInt(count));
	}

	@Override
	public boolean hasReaders() {
		return committed.get() < skip + limit;
	}

	@Override
	public @Nullable FrameSlot acquire(List<StreamSpec> streams) {
		return hasReaders() ? new Slot(streams) : null;
	}

	private void onCommit(Slot slot, long frameIndex) {
		int ordinal = committed.getAndIncrement();
		if (ordinal >= skip && ordinal < skip + limit) {
			writer.execute(() -> FrameFiles.write(directory, frameIndex, slot.streams, slot.buffers, slot.meta));
		}
	}

	private final class Slot implements FrameSlot {
		private final List<StreamSpec> streams;
		private final Map<String, ByteBuffer> buffers = new LinkedHashMap<>();
		private Map<String, Object> meta = Map.of();

		private Slot(List<StreamSpec> streams) {
			this.streams = streams;
			for (StreamSpec spec : streams) {
				buffers.put(spec.name(), ByteBuffer.allocate(Math.toIntExact(spec.byteSize())));
			}
		}

		@Override
		public ByteBuffer stream(String name) {
			ByteBuffer buffer = buffers.get(name);
			if (buffer == null) {
				throw new IllegalArgumentException("No stream " + name);
			}
			return buffer.clear();
		}

		@Override
		public void setMeta(Map<String, Object> meta) {
			this.meta = meta;
		}

		@Override
		public void commit(long frameIndex, long captureNanos) {
			onCommit(this, frameIndex);
		}

		@Override
		public void abort() {
		}
	}
}
