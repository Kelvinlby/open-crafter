package mod.kelvinlby.opencrafter.transport.shm;

/**
 * Byte layout of the shared-memory frame ring (all integers little-endian, every u64
 * 8-byte aligned). The authoritative description is {@code docs/PROTOCOL.md}.
 */
public final class RingLayout {
	/** "OCFR" read as little-endian bytes. */
	public static final int MAGIC = 0x5246434F;
	public static final int VERSION = 1;

	public static final int HEADER_SIZE = 4096;
	public static final int H_MAGIC = 0;
	public static final int H_VERSION = 4;
	public static final int H_GENERATION = 8;
	public static final int H_SLOT_COUNT = 16;
	public static final int H_MAX_STREAMS = 20;
	public static final int H_SLOT_SIZE = 24;
	public static final int H_SLOTS_OFFSET = 32;
	public static final int H_LATEST = 40;
	public static final int H_WRITER_PID = 48;
	public static final int H_SLOT_HEADER_SIZE = 56;
	public static final int H_META_CAPACITY = 60;

	public static final int S_SEQ = 0;
	public static final int S_FRAME_INDEX = 8;
	public static final int S_CAPTURE_NANOS = 16;
	public static final int S_COMMIT = 24;
	public static final int S_STREAM_COUNT = 32;
	public static final int S_META_SIZE = 36;
	public static final int S_STREAM_TABLE = 64;

	public static final int MAX_STREAMS = 16;
	public static final int STREAM_ENTRY_SIZE = 64;
	public static final int E_NAME = 0;
	public static final int NAME_BYTES = 32;
	public static final int E_DTYPE = 32;
	public static final int DTYPE_BYTES = 4;
	public static final int E_CHANNELS = 36;
	public static final int E_WIDTH = 40;
	public static final int E_HEIGHT = 44;
	public static final int E_OFFSET = 48;
	public static final int E_SIZE = 56;

	public static final int S_META = S_STREAM_TABLE + MAX_STREAMS * STREAM_ENTRY_SIZE;
	public static final int SLOT_HEADER_SIZE = 64 * 1024;
	public static final int META_CAPACITY = SLOT_HEADER_SIZE - S_META;

	public static final int DATA_ALIGNMENT = 64;
	public static final int SLOT_ALIGNMENT = 4096;

	/** The low bits of {@code H_LATEST} hold the slot index, the high bits the commit sequence. */
	public static final int LATEST_SLOT_BITS = 16;
	public static final int MAX_SLOTS = (1 << LATEST_SLOT_BITS) - 1;

	private RingLayout() {
	}

	public static long packLatest(long commitSequence, int slot) {
		return commitSequence << LATEST_SLOT_BITS | slot;
	}

	public static long streamEntry(int index) {
		return S_STREAM_TABLE + (long) index * STREAM_ENTRY_SIZE;
	}

	public static long alignUp(long value, long alignment) {
		return (value + alignment - 1) / alignment * alignment;
	}
}
