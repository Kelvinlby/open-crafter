package mod.kelvinlby.opencrafter.transport.shm;

import mod.kelvinlby.opencrafter.api.frame.DType;
import mod.kelvinlby.opencrafter.api.frame.FrameSlot;
import mod.kelvinlby.opencrafter.api.frame.StreamSpec;
import mod.kelvinlby.opencrafter.transport.msgpack.MsgPackReader;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static mod.kelvinlby.opencrafter.transport.shm.RingLayout.*;
import static org.junit.jupiter.api.Assertions.*;

class FrameRingTest {
	private static final StreamSpec RGB = new StreamSpec("rgb", DType.UINT8, 4, 8, 4);
	private static final StreamSpec FLOW = new StreamSpec("flow", DType.FLOAT32, 2, 8, 4);

	@TempDir
	Path dir;
	private Path path;
	private FrameRing ring;
	private final List<long[]> commits = new ArrayList<>();

	@BeforeEach
	void open() throws IOException {
		path = dir.resolve("ring");
		ring = FrameRing.create(path, 3, () -> true, (index, slot, seq) -> commits.add(new long[] {index, slot, seq}));
	}

	@AfterEach
	void close() {
		ring.close();
	}

	/** Reads the file through an independent mapping, like an external backend would. */
	private ByteBuffer readFile() throws IOException {
		try (FileChannel channel = FileChannel.open(path, StandardOpenOption.READ)) {
			return channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size()).order(ByteOrder.LITTLE_ENDIAN);
		}
	}

	private static long slotBase(ByteBuffer file, int slot) {
		return file.getLong(H_SLOTS_OFFSET) + slot * file.getLong(H_SLOT_SIZE);
	}

	private static int latestSlot(ByteBuffer file) {
		return (int) (file.getLong(H_LATEST) & MAX_SLOTS);
	}

	private static long latestSequence(ByteBuffer file) {
		return file.getLong(H_LATEST) >>> LATEST_SLOT_BITS;
	}

	private static String fixedString(ByteBuffer file, long offset, int width) {
		byte[] bytes = new byte[width];
		file.get((int) offset, bytes);
		int end = 0;
		while (end < width && bytes[end] != 0) {
			end++;
		}
		return new String(bytes, 0, end, StandardCharsets.UTF_8);
	}

	private FrameSlot commitFrame(long frameIndex, byte fill) {
		FrameSlot slot = ring.acquire(List.of(RGB));
		ByteBuffer rgb = slot.stream("rgb");
		while (rgb.hasRemaining()) {
			rgb.put(fill);
		}
		slot.commit(frameIndex, frameIndex * 1000);
		return slot;
	}

	@Test
	void freshRingHasValidHeaderAndNoFrame() throws IOException {
		ByteBuffer file = readFile();
		assertEquals(MAGIC, file.getInt(H_MAGIC));
		assertEquals(VERSION, file.getInt(H_VERSION));
		assertEquals(2, file.getLong(H_GENERATION));
		assertEquals(3, file.getInt(H_SLOT_COUNT));
		assertEquals(HEADER_SIZE, file.getLong(H_SLOTS_OFFSET));
		assertEquals(SLOT_HEADER_SIZE, file.getLong(H_SLOT_SIZE));
		assertEquals(0, file.getLong(H_LATEST));
		assertEquals(ProcessHandle.current().pid(), file.getLong(H_WRITER_PID));
		assertEquals(MAGIC, ByteBuffer.wrap("OCFR".getBytes(StandardCharsets.US_ASCII)).order(ByteOrder.LITTLE_ENDIAN).getInt());
	}

	@Test
	void commitPublishesStreamsMetaAndLatestPointer() throws IOException {
		FrameSlot slot = ring.acquire(List.of(RGB, FLOW));
		slot.stream("rgb").put(0, (byte) 42);
		slot.stream("flow").putFloat(4, 1.5f);
		slot.setMeta(Map.of("tick", 99));
		slot.commit(7, 123_456);

		ByteBuffer file = readFile();
		int index = latestSlot(file);
		long base = slotBase(file, index);
		assertEquals(1, latestSequence(file));
		assertEquals(0, file.getLong((int) base + S_SEQ) & 1, "seqlock even after commit");
		assertEquals(7, file.getLong((int) base + S_FRAME_INDEX));
		assertEquals(123_456, file.getLong((int) base + S_CAPTURE_NANOS));
		assertEquals(1, file.getLong((int) base + S_COMMIT));
		assertEquals(2, file.getInt((int) base + S_STREAM_COUNT));

		long rgbEntry = base + streamEntry(0);
		assertEquals("rgb", fixedString(file, rgbEntry + E_NAME, NAME_BYTES));
		assertEquals("<u1", fixedString(file, rgbEntry + E_DTYPE, DTYPE_BYTES));
		assertEquals(4, file.getInt((int) (rgbEntry + E_CHANNELS)));
		assertEquals(8, file.getInt((int) (rgbEntry + E_WIDTH)));
		assertEquals(4, file.getInt((int) (rgbEntry + E_HEIGHT)));
		assertEquals(RGB.byteSize(), file.getLong((int) (rgbEntry + E_SIZE)));
		long rgbOffset = file.getLong((int) (rgbEntry + E_OFFSET));
		assertEquals(42, file.get((int) (base + rgbOffset)));

		long flowEntry = base + streamEntry(1);
		assertEquals("<f4", fixedString(file, flowEntry + E_DTYPE, DTYPE_BYTES));
		long flowOffset = file.getLong((int) (flowEntry + E_OFFSET));
		assertEquals(0, flowOffset % DATA_ALIGNMENT);
		assertTrue(flowOffset >= rgbOffset + RGB.byteSize());
		assertEquals(1.5f, file.getFloat((int) (base + flowOffset + 4)));

		int metaSize = file.getInt((int) base + S_META_SIZE);
		byte[] meta = new byte[metaSize];
		file.get((int) base + S_META, meta);
		assertEquals(Map.of("tick", 99L), MsgPackReader.decode(meta));

		assertEquals(1, commits.size());
		assertArrayEquals(new long[] {7, index, 1}, commits.getFirst());
	}

	@Test
	void slotIsOddWhileBeingWritten() throws IOException {
		FrameSlot slot = ring.acquire(List.of(RGB));
		ByteBuffer file = readFile();
		long oddSlots = 0;
		for (int i = 0; i < 3; i++) {
			oddSlots += file.getLong((int) slotBase(file, i) + S_SEQ) & 1;
		}
		assertEquals(1, oddSlots);
		slot.abort();
	}

	@Test
	void acquireNeverReturnsLatestOrInFlightSlot() throws IOException {
		commitFrame(1, (byte) 1);
		int latest = latestSlot(readFile());
		FrameSlot a = ring.acquire(List.of(RGB));
		FrameSlot b = ring.acquire(List.of(RGB));
		assertNotNull(a);
		assertNotNull(b);
		assertNull(ring.acquire(List.of(RGB)), "all non-latest slots are in flight");
		assertNotEquals(latest, ((RingSlot) a).index);
		assertNotEquals(latest, ((RingSlot) b).index);
		a.abort();
		assertNotNull(ring.acquire(List.of(RGB)));
		b.abort();
	}

	@Test
	void acquirePicksLeastRecentlyCommittedSlot() {
		List<Integer> order = new ArrayList<>();
		for (int frame = 0; frame < 9; frame++) {
			RingSlot slot = (RingSlot) ring.acquire(List.of(RGB));
			order.add(slot.index);
			slot.commit(frame, 0);
		}
		assertEquals(List.of(0, 1, 2, 0, 1, 2, 0, 1, 2), order);
	}

	@Test
	void abortedSlotIsReusedFirstAndLatestIsUnchanged() throws IOException {
		commitFrame(1, (byte) 1);
		long latestBefore = readFile().getLong(H_LATEST);
		RingSlot aborted = (RingSlot) ring.acquire(List.of(RGB));
		aborted.abort();
		assertEquals(latestBefore, readFile().getLong(H_LATEST));
		RingSlot next = (RingSlot) ring.acquire(List.of(RGB));
		assertEquals(aborted.index, next.index);
		next.abort();
	}

	@Test
	void finishingTwiceIsRejected() {
		FrameSlot slot = commitFrame(1, (byte) 1);
		assertThrows(IllegalStateException.class, () -> slot.commit(2, 0));
		assertThrows(IllegalStateException.class, slot::abort);
		assertThrows(IllegalStateException.class, () -> slot.setMeta(Map.of()));
	}

	@Test
	void largerFrameGrowsRingUnderNewGeneration() throws IOException {
		commitFrame(1, (byte) 1);
		long generation = ring.generation();
		StreamSpec big = new StreamSpec("rgb", DType.UINT8, 4, 640, 360);
		FrameSlot slot = ring.acquire(List.of(big));
		assertNotNull(slot);
		assertEquals(generation + 2, ring.generation(), "generation advances by two (odd while changing)");
		assertTrue(ring.slotSize() >= SLOT_HEADER_SIZE + big.byteSize());
		assertEquals(HEADER_SIZE, ring.slotsOffset(), "nothing in flight: re-laid-out in place");
		ByteBuffer file = readFile();
		assertEquals(0, file.getLong(H_LATEST), "old frames are gone after a re-layout");
		assertTrue(file.capacity() >= HEADER_SIZE + 3 * ring.slotSize());

		slot.stream("rgb").put((int) big.byteSize() - 1, (byte) 5);
		slot.commit(2, 0);
		file = readFile();
		long base = slotBase(file, latestSlot(file));
		assertEquals(2, file.getLong((int) base + S_FRAME_INDEX));
	}

	@Test
	void growWhileSlotInFlightRelocatesAndDropsStaleCommit() throws IOException {
		FrameSlot stale = ring.acquire(List.of(RGB));
		long sizeBefore = Files.size(path);
		FrameSlot fresh = ring.acquire(List.of(new StreamSpec("rgb", DType.UINT8, 4, 256, 256)));
		assertNotNull(fresh);
		assertTrue(ring.slotsOffset() >= sizeBefore, "new layout must not overlap the in-flight slot");

		ByteBuffer staleBuffer = stale.stream("rgb");
		staleBuffer.put(0, (byte) 9);
		stale.setMeta(Map.of("ignored", true));
		stale.commit(1, 0);
		assertEquals(0, readFile().getLong(H_LATEST), "stale commit is dropped");
		assertTrue(commits.isEmpty());

		fresh.commit(2, 0);
		assertEquals(1, commits.size());
		assertEquals(2, commits.getFirst()[0]);
	}

	@Test
	void smallerFrameReusesExistingSlots() {
		FrameSlot big = ring.acquire(List.of(new StreamSpec("rgb", DType.UINT8, 4, 128, 128)));
		big.commit(1, 0);
		long generation = ring.generation();
		commitFrame(2, (byte) 2);
		assertEquals(generation, ring.generation());
	}

	@Test
	void invalidStreamsAreRejected() {
		assertThrows(IllegalArgumentException.class, () -> ring.acquire(List.of(RGB, RGB)));
		assertThrows(IllegalArgumentException.class, () -> ring.acquire(List.of(new StreamSpec("", DType.UINT8, 1, 1, 1))));
		assertThrows(IllegalArgumentException.class, () -> ring.acquire(List.of(new StreamSpec("x".repeat(32), DType.UINT8, 1, 1, 1))));
		assertThrows(IllegalArgumentException.class, () -> ring.acquire(List.of(new StreamSpec("z", DType.UINT8, 0, 1, 1))));
		List<StreamSpec> many = new ArrayList<>();
		for (int i = 0; i <= MAX_STREAMS; i++) {
			many.add(new StreamSpec("s" + i, DType.UINT8, 1, 1, 1));
		}
		assertThrows(IllegalArgumentException.class, () -> ring.acquire(many));
	}

	@Test
	void unknownStreamAndOversizedMetaAreRejected() {
		FrameSlot slot = ring.acquire(List.of(RGB));
		assertThrows(IllegalArgumentException.class, () -> slot.stream("depth"));
		assertThrows(IllegalArgumentException.class, () -> slot.setMeta(Map.of("blob", new byte[META_CAPACITY])));
		assertThrows(IllegalArgumentException.class, () -> slot.setMeta(Map.of("bad", new Object())));
		slot.abort();
	}

	@Test
	void streamBufferIsLittleEndianAndExactlySized() {
		FrameSlot slot = ring.acquire(List.of(FLOW));
		ByteBuffer flow = slot.stream("flow");
		assertEquals(0, flow.position());
		assertEquals(FLOW.byteSize(), flow.limit());
		assertEquals(ByteOrder.LITTLE_ENDIAN, flow.order());
		slot.abort();
	}

	@Test
	void closeDeletesFileAndStopsHandingOutSlots() {
		ring.close();
		assertFalse(Files.exists(path));
		assertNull(ring.acquire(List.of(RGB)));
	}

	@Test
	void slotCountIsValidated() {
		assertThrows(IllegalArgumentException.class, () -> FrameRing.create(dir.resolve("one"), 1, () -> true, (a, b, c) -> {
		}));
	}
}
