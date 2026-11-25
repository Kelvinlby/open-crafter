package mod.kelvinlby.opencrafter.transport.shm;

import java.io.IOException;
import java.lang.foreign.Arena;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.ValueLayout;
import java.lang.invoke.VarHandle;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;

/**
 * One memory mapping of the ring file for one layout generation, with little-endian
 * accessors. Reference counted (the ring plus every in-flight slot); the mapping is
 * unmapped when the last reference is released. Reference counting is guarded by the
 * ring's lock.
 */
final class RingMapping {
	private static final ValueLayout.OfLong U64 = ValueLayout.JAVA_LONG.withOrder(ByteOrder.LITTLE_ENDIAN);
	private static final ValueLayout.OfInt U32 = ValueLayout.JAVA_INT.withOrder(ByteOrder.LITTLE_ENDIAN);
	private static final VarHandle U64_HANDLE = U64.varHandle();

	final long size;
	final int slotCount;
	final long slotSize;
	final long slotsOffset;
	private final Arena arena;
	private final MemorySegment segment;
	private int references = 1;

	private RingMapping(Arena arena, MemorySegment segment, int slotCount, long slotSize, long slotsOffset) {
		this.arena = arena;
		this.segment = segment;
		this.size = segment.byteSize();
		this.slotCount = slotCount;
		this.slotSize = slotSize;
		this.slotsOffset = slotsOffset;
	}

	static RingMapping map(FileChannel channel, long size, int slotCount, long slotSize, long slotsOffset) throws IOException {
		Arena arena = Arena.ofShared();
		try {
			MemorySegment segment = channel.map(FileChannel.MapMode.READ_WRITE, 0, size, arena);
			return new RingMapping(arena, segment, slotCount, slotSize, slotsOffset);
		} catch (IOException | RuntimeException e) {
			arena.close();
			throw e;
		}
	}

	long slotBase(int slot) {
		return slotsOffset + slot * slotSize;
	}

	void retain() {
		references++;
	}

	void release() {
		if (--references == 0) {
			arena.close();
		}
	}

	long getLong(long offset) {
		return segment.get(U64, offset);
	}

	void putLong(long offset, long value) {
		segment.set(U64, offset, value);
	}

	int getInt(long offset) {
		return segment.get(U32, offset);
	}

	void putInt(long offset, int value) {
		segment.set(U32, offset, value);
	}

	/** Store that later stores may not overtake (seqlock "begin write"). */
	void putLongBeforeLaterStores(long offset, long value) {
		U64_HANDLE.setOpaque(segment, offset, value);
		VarHandle.storeStoreFence();
	}

	/** Release store: every earlier write is visible to a reader that observes this value. */
	void putLongRelease(long offset, long value) {
		U64_HANDLE.setRelease(segment, offset, value);
	}

	/** Full-fence store, for rare layout changes. */
	void putLongVolatile(long offset, long value) {
		U64_HANDLE.setVolatile(segment, offset, value);
	}

	long getLongAcquire(long offset) {
		return (long) U64_HANDLE.getAcquire(segment, offset);
	}

	/** Writes {@code bytes} into a fixed-width field, NUL-padding the rest. */
	void putPadded(long offset, byte[] bytes, int width) {
		MemorySegment.copy(bytes, 0, segment, ValueLayout.JAVA_BYTE, offset, bytes.length);
		segment.asSlice(offset + bytes.length, width - bytes.length).fill((byte) 0);
	}

	void putBytes(long offset, byte[] bytes, int length) {
		MemorySegment.copy(bytes, 0, segment, ValueLayout.JAVA_BYTE, offset, length);
	}

	/** Little-endian view of {@code [offset, offset + length)}. */
	ByteBuffer buffer(long offset, long length) {
		return segment.asSlice(offset, length).asByteBuffer().order(ByteOrder.LITTLE_ENDIAN);
	}
}
