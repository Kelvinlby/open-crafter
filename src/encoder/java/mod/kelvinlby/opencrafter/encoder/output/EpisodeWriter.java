package mod.kelvinlby.opencrafter.encoder.output;

import mod.kelvinlby.opencrafter.encoder.pipeline.EncodeConfig;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Writes one episode's frames on its own thread, so encoding overlaps rendering. The render thread
 * hands over frames through a short queue and waits only when the writer falls behind.
 *
 * <p>Files: {@code rgb.mkv} (or as configured), {@code flow.ocs}, {@code flow_occluded.ocs},
 * {@code frames.jsonl.zst} (one line of metadata per frame, in frame order).
 */
public final class EpisodeWriter implements AutoCloseable {
	private static final int QUEUE_FRAMES = 6;
	private static final Frame END = new Frame(-1, Map.of(), Map.of());

	private final Path dir;
	private final EncodeConfig config;
	private final BlockingQueue<Frame> queue = new ArrayBlockingQueue<>(QUEUE_FRAMES);
	private final Map<String, ArrayDeque<ByteBuffer>> pool = new ConcurrentHashMap<>();
	private final Map<String, AutoCloseable> writers = new LinkedHashMap<>();
	private final JsonlWriter frames;
	private final Thread thread;
	private volatile @Nullable Throwable failure;
	private long written;

	/** One captured frame: its streams (owned by the writer until written) and metadata. */
	public record Frame(long index, Map<String, ByteBuffer> streams, Map<String, Object> meta) {
	}

	public EpisodeWriter(Path dir, EncodeConfig config) throws IOException {
		this.dir = dir;
		this.config = config;
		Files.createDirectories(dir);
		this.frames = new JsonlWriter(dir.resolve("frames.jsonl.zst"), 6);
		this.thread = Thread.ofPlatform().name("open-crafter-encoder-writer").daemon().start(this::run);
	}

	/** A buffer for one frame of a stream, reused once written. */
	public ByteBuffer borrow(String stream, int bytes) {
		ArrayDeque<ByteBuffer> free = pool.computeIfAbsent(stream, s -> new ArrayDeque<>());
		ByteBuffer buffer;
		synchronized (free) {
			buffer = free.poll();
		}
		if (buffer == null || buffer.capacity() != bytes) {
			buffer = ByteBuffer.allocateDirect(bytes).order(ByteOrder.LITTLE_ENDIAN);
		}
		return buffer.clear();
	}

	/** Queues a frame; blocks while the writer is behind. */
	public void submit(Frame frame) {
		rethrowFailure();
		try {
			queue.put(frame);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while queueing a frame", e);
		}
	}

	public long written() {
		return written;
	}

	@Override
	public void close() throws IOException {
		try {
			queue.put(END);
			thread.join();
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			throw new IOException("Interrupted while finishing the episode", e);
		}
		IOException closing = closeWriters();
		rethrowFailure();
		if (closing != null) {
			throw closing;
		}
	}

	/** Bytes per stream on disk, for the episode summary. */
	public Map<String, Long> storedBytes() {
		Map<String, Long> sizes = new LinkedHashMap<>();
		try (var files = Files.list(dir)) {
			files.filter(Files::isRegularFile).forEach(f -> sizes.put(f.getFileName().toString(), f.toFile().length()));
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
		return sizes;
	}

	private void run() {
		try {
			for (Frame frame = queue.take(); frame != END; frame = queue.take()) {
				writeFrame(frame);
			}
		} catch (Throwable t) {
			failure = t;
			queue.clear();
		}
	}

	private void writeFrame(Frame frame) throws Exception {
		for (Map.Entry<String, ByteBuffer> stream : frame.streams().entrySet()) {
			writeStream(stream.getKey(), stream.getValue());
			giveBack(stream.getKey(), stream.getValue());
		}
		frames.write(frame.meta());
		written++;
	}

	private void writeStream(String name, ByteBuffer data) throws IOException {
		AutoCloseable writer = writers.get(name);
		if (writer == null) {
			writer = openWriter(name);
			writers.put(name, writer);
		}
		data.rewind();
		switch (writer) {
			case RgbVideoWriter rgb -> rgb.write(data);
			case ArrayStreamWriter array -> array.write(data);
			default -> throw new IllegalStateException("No writer for " + name);
		}
	}

	private AutoCloseable openWriter(String stream) throws IOException {
		return switch (stream) {
			case "rgb" -> new RgbVideoWriter(dir, config);
			case "flow" -> new ArrayStreamWriter(dir.resolve("flow.ocs"), "flow", config.width, config.height,
				ArrayCodec.forFlow(config.flow.precision, config.flow.q16Scale), config.flow.zstdLevel);
			case "flow_occluded" -> new ArrayStreamWriter(dir.resolve("flow_occluded.ocs"), "flow_occluded", config.width, config.height,
				new ArrayCodec.Bits(), config.flow.zstdLevel);
			default -> throw new IOException("Unknown vision stream " + stream);
		};
	}

	private void giveBack(String stream, ByteBuffer buffer) {
		ArrayDeque<ByteBuffer> free = pool.computeIfAbsent(stream, s -> new ArrayDeque<>());
		synchronized (free) {
			if (free.size() < QUEUE_FRAMES + 2) {
				free.push(buffer);
			}
		}
	}

	private @Nullable IOException closeWriters() {
		IOException first = null;
		for (AutoCloseable writer : writers.values()) {
			first = closeQuietly(writer, first);
		}
		return closeQuietly(frames, first);
	}

	private static @Nullable IOException closeQuietly(AutoCloseable closeable, @Nullable IOException first) {
		try {
			closeable.close();
			return first;
		} catch (Exception e) {
			IOException error = e instanceof IOException io ? io : new IOException(e);
			if (first != null) {
				first.addSuppressed(error);
				return first;
			}
			return error;
		}
	}

	private void rethrowFailure() {
		Throwable t = failure;
		if (t != null) {
			throw new IllegalStateException("Episode writer failed", t);
		}
	}
}
