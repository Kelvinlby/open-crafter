package mod.kelvinlby.opencrafter.transport.shm;

import mod.kelvinlby.opencrafter.api.frame.FramePublisher;
import mod.kelvinlby.opencrafter.api.frame.FrameSlot;
import mod.kelvinlby.opencrafter.api.frame.StreamSpec;
import mod.kelvinlby.opencrafter.transport.fs.PrivateFiles;
import mod.kelvinlby.opencrafter.transport.msgpack.MsgPackException;
import mod.kelvinlby.opencrafter.transport.msgpack.MsgPackWriter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.BooleanSupplier;

import static mod.kelvinlby.opencrafter.transport.shm.RingLayout.*;

/**
 * Single-writer shared-memory frame ring (see {@code docs/PROTOCOL.md}).
 *
 * <p>Each slot is guarded by a seqlock: odd while being written, even when stable. The
 * writer never waits for readers: {@link #acquire} takes the least recently committed slot
 * that is neither the latest committed one nor already being written, and returns
 * {@code null} when there is none. When a frame needs more room than a slot has, the file
 * is grown and re-laid-out under a new header generation (odd while changing). Slots that
 * were in flight during a re-layout keep writing into a region the new layout does not
 * use, and their commit is dropped.
 *
 * <p>All methods synchronise on the ring; only producers (render/main thread or readback
 * callbacks) take that lock, never the transport.
 */
public final class FrameRing implements FramePublisher, AutoCloseable {
	private static final Logger LOGGER = LoggerFactory.getLogger("open-crafter/transport");
	private static final long INITIAL_GENERATION = 0;

	/** Called after every commit, outside the ring's lock. Must not block. */
	@FunctionalInterface
	public interface CommitListener {
		void committed(long frameIndex, int slot, long commitSequence);
	}

	private final Path path;
	private final FileChannel channel;
	private final int slotCount;
	private final BooleanSupplier readers;
	private final CommitListener listener;
	private final boolean[] writing;
	private final long[] sequences;
	private final long[] committedAt;
	private final MsgPackWriter metaWriter = new MsgPackWriter(1024);
	private RingMapping mapping;
	private long generation = INITIAL_GENERATION;
	private long commitCounter;
	private int latestSlot = -1;
	private int inFlight;
	private boolean closed;

	private FrameRing(Path path, FileChannel channel, int slotCount, BooleanSupplier readers, CommitListener listener) {
		this.path = path;
		this.channel = channel;
		this.slotCount = slotCount;
		this.readers = readers;
		this.listener = listener;
		this.writing = new boolean[slotCount];
		this.sequences = new long[slotCount];
		this.committedAt = new long[slotCount];
	}

	/** Creates (replacing any stale file) and lays out a ring of {@code slotCount} slots. */
	public static FrameRing create(Path path, int slotCount, BooleanSupplier readers, CommitListener listener) throws IOException {
		if (slotCount < 2 || slotCount > MAX_SLOTS) {
			throw new IllegalArgumentException("ringSlots must be 2.." + MAX_SLOTS + ", got " + slotCount);
		}
		PrivateFiles.prepareFresh(path);
		FileChannel channel = FileChannel.open(path, StandardOpenOption.CREATE_NEW, StandardOpenOption.READ, StandardOpenOption.WRITE);
		try {
			PrivateFiles.restrictToOwner(path);
			FrameRing ring = new FrameRing(path, channel, slotCount, readers, listener);
			ring.initialize();
			return ring;
		} catch (IOException | RuntimeException e) {
			channel.close();
			Files.deleteIfExists(path);
			throw e;
		}
	}

	public Path path() {
		return path;
	}

	@Override
	public boolean hasReaders() {
		return readers.getAsBoolean();
	}

	@Override
	public synchronized FrameSlot acquire(List<StreamSpec> streams) {
		if (closed) {
			return null;
		}
		SlotPlan plan = SlotPlan.of(streams);
		if (!ensureSlotSize(plan.slotBytes())) {
			return null;
		}
		int slot = pickSlot();
		return slot < 0 ? null : beginWrite(slot, plan);
	}

	@Override
	public void close() {
		synchronized (this) {
			if (closed) {
				return;
			}
			closed = true;
			mapping.release();
		}
		closeChannel();
		deleteFile();
	}

	synchronized long generation() {
		return generation;
	}

	synchronized long slotSize() {
		return mapping.slotSize;
	}

	synchronized long slotsOffset() {
		return mapping.slotsOffset;
	}

	synchronized void writeMeta(RingSlot slot, Map<String, Object> meta) {
		requireUnfinished(slot);
		byte[] encoded = encodeMeta(meta);
		slot.mapping.putBytes(slot.base + S_META, encoded, encoded.length);
		slot.mapping.putInt(slot.base + S_META_SIZE, encoded.length);
	}

	void commit(RingSlot slot, long frameIndex, long captureNanos) {
		long sequence;
		synchronized (this) {
			requireUnfinished(slot);
			sequence = finish(slot) ? publish(slot, frameIndex, captureNanos) : 0;
		}
		if (sequence != 0) {
			listener.committed(frameIndex, slot.index, sequence);
		}
	}

	synchronized void abort(RingSlot slot) {
		requireUnfinished(slot);
		if (finish(slot)) {
			slot.mapping.putLong(slot.base + S_COMMIT, 0);
			endWrite(slot.index);
			committedAt[slot.index] = 0;
		}
	}

	private void initialize() throws IOException {
		relayout(HEADER_SIZE, SLOT_HEADER_SIZE);
		writeIdentity();
	}

	private boolean ensureSlotSize(long required) {
		if (required <= mapping.slotSize) {
			return true;
		}
		try {
			grow(required);
			return true;
		} catch (IOException e) {
			LOGGER.error("Could not grow the frame ring to {} bytes per slot", required, e);
			return false;
		}
	}

	/** Grows by at least 1.5x; relocates past the file end while any slot is still in flight. */
	private void grow(long required) throws IOException {
		long slotSize = alignUp(Math.max(required, mapping.slotSize + mapping.slotSize / 2), SLOT_ALIGNMENT);
		long slotsOffset = inFlight > 0 ? alignUp(channel.size(), SLOT_ALIGNMENT) : HEADER_SIZE;
		relayout(slotsOffset, slotSize);
	}

	/** Re-lays-out the ring under a new generation: odd while changing, even when done. */
	private void relayout(long slotsOffset, long slotSize) throws IOException {
		RingMapping previous = mapping;
		long changing = generation + 1;
		if (previous != null) {
			previous.putLongVolatile(H_GENERATION, changing);
		}
		long fileSize = slotsOffset + slotCount * slotSize;
		ensureFileSize(fileSize);
		RingMapping next = RingMapping.map(channel, fileSize, slotCount, slotSize, slotsOffset);
		next.putLongVolatile(H_GENERATION, changing);
		writeLayout(next);
		resetBookkeeping();
		mapping = next;
		generation = changing + 1;
		next.putLongRelease(H_GENERATION, generation);
		if (previous != null) {
			previous.release();
		}
	}

	private void writeLayout(RingMapping target) {
		target.putInt(H_SLOT_COUNT, slotCount);
		target.putInt(H_MAX_STREAMS, MAX_STREAMS);
		target.putLong(H_SLOT_SIZE, target.slotSize);
		target.putLong(H_SLOTS_OFFSET, target.slotsOffset);
		target.putLong(H_LATEST, 0);
		target.putLong(H_WRITER_PID, ProcessHandle.current().pid());
		target.putInt(H_SLOT_HEADER_SIZE, SLOT_HEADER_SIZE);
		target.putInt(H_META_CAPACITY, META_CAPACITY);
		for (int slot = 0; slot < slotCount; slot++) {
			clearSlotHeader(target, target.slotBase(slot));
		}
	}

	private static void clearSlotHeader(RingMapping target, long base) {
		target.putLong(base + S_SEQ, 0);
		target.putLong(base + S_FRAME_INDEX, 0);
		target.putLong(base + S_CAPTURE_NANOS, 0);
		target.putLong(base + S_COMMIT, 0);
		target.putInt(base + S_STREAM_COUNT, 0);
		target.putInt(base + S_META_SIZE, 0);
	}

	/** Magic and version go last, so a reader never sees a half-initialised file as valid. */
	private void writeIdentity() {
		mapping.putInt(H_VERSION, VERSION);
		VarHandle.storeStoreFence();
		mapping.putInt(H_MAGIC, MAGIC);
	}

	private void resetBookkeeping() {
		Arrays.fill(writing, false);
		Arrays.fill(sequences, 0);
		Arrays.fill(committedAt, 0);
		latestSlot = -1;
	}

	private void ensureFileSize(long size) throws IOException {
		if (channel.size() < size) {
			channel.write(ByteBuffer.wrap(new byte[1]), size - 1);
		}
	}

	/** Least recently committed slot that is neither being written nor the latest frame. */
	private int pickSlot() {
		int best = -1;
		for (int slot = 0; slot < slotCount; slot++) {
			if (!writing[slot] && slot != latestSlot && (best < 0 || committedAt[slot] < committedAt[best])) {
				best = slot;
			}
		}
		return best;
	}

	private RingSlot beginWrite(int index, SlotPlan plan) {
		writing[index] = true;
		inFlight++;
		mapping.retain();
		RingSlot slot = new RingSlot(this, mapping, index, plan);
		mapping.putLongBeforeLaterStores(slot.base + S_SEQ, ++sequences[index]);
		writeStreamTable(slot);
		mapping.putInt(slot.base + S_META_SIZE, 0);
		return slot;
	}

	private void writeStreamTable(RingSlot slot) {
		List<StreamSpec> streams = slot.plan.streams();
		for (int i = 0; i < streams.size(); i++) {
			writeStreamEntry(slot.base + streamEntry(i), streams.get(i), slot.plan.offsets()[i]);
		}
		mapping.putInt(slot.base + S_STREAM_COUNT, streams.size());
	}

	private void writeStreamEntry(long entry, StreamSpec spec, long offset) {
		mapping.putPadded(entry + E_NAME, SlotPlan.nameBytes(spec), NAME_BYTES);
		mapping.putPadded(entry + E_DTYPE, SlotPlan.dtypeBytes(spec), DTYPE_BYTES);
		mapping.putInt(entry + E_CHANNELS, spec.channels());
		mapping.putInt(entry + E_WIDTH, spec.width());
		mapping.putInt(entry + E_HEIGHT, spec.height());
		mapping.putLong(entry + E_OFFSET, offset);
		mapping.putLong(entry + E_SIZE, spec.byteSize());
	}

	/** Marks the slot finished; true when it still belongs to the live layout. */
	private boolean finish(RingSlot slot) {
		slot.finished = true;
		inFlight--;
		boolean live = !closed && slot.mapping == mapping;
		if (live) {
			writing[slot.index] = false;
		}
		slot.mapping.release();
		return live;
	}

	private long publish(RingSlot slot, long frameIndex, long captureNanos) {
		long sequence = ++commitCounter;
		mapping.putLong(slot.base + S_FRAME_INDEX, frameIndex);
		mapping.putLong(slot.base + S_CAPTURE_NANOS, captureNanos);
		mapping.putLong(slot.base + S_COMMIT, sequence);
		endWrite(slot.index);
		mapping.putLongRelease(H_LATEST, packLatest(sequence, slot.index));
		committedAt[slot.index] = sequence;
		latestSlot = slot.index;
		return sequence;
	}

	private void endWrite(int index) {
		mapping.putLongRelease(mapping.slotBase(index) + S_SEQ, ++sequences[index]);
	}

	private byte[] encodeMeta(Map<String, Object> meta) {
		metaWriter.reset();
		try {
			metaWriter.writeValue(meta == null ? Map.of() : meta);
		} catch (MsgPackException e) {
			throw new IllegalArgumentException("Frame meta is not encodable: " + e.getMessage(), e);
		}
		if (metaWriter.size() > META_CAPACITY) {
			throw new IllegalArgumentException("Frame meta is " + metaWriter.size() + " bytes, limit " + META_CAPACITY);
		}
		return metaWriter.toByteArray();
	}

	private static void requireUnfinished(RingSlot slot) {
		if (slot.finished) {
			throw new IllegalStateException("Frame slot already committed or aborted");
		}
	}

	private void closeChannel() {
		try {
			channel.close();
		} catch (IOException e) {
			LOGGER.debug("Closing frame ring failed", e);
		}
	}

	private void deleteFile() {
		try {
			Files.deleteIfExists(path);
		} catch (IOException e) {
			LOGGER.debug("Could not delete frame ring {}", path, e);
		}
	}
}
