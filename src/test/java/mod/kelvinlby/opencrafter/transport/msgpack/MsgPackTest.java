package mod.kelvinlby.opencrafter.transport.msgpack;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class MsgPackTest {
	private static byte[] encode(Object value) {
		return new MsgPackWriter(16).writeValue(value).toByteArray();
	}

	private static Object roundTrip(Object value) {
		return MsgPackReader.decode(encode(value));
	}

	private static byte[] bytes(int... values) {
		byte[] out = new byte[values.length];
		for (int i = 0; i < values.length; i++) {
			out[i] = (byte) values[i];
		}
		return out;
	}

	@ParameterizedTest
	@ValueSource(longs = {0, 1, 127, 128, 255, 256, 65535, 65536, 0xffffffffL, 0x100000000L, Long.MAX_VALUE,
		-1, -32, -33, -128, -129, -32768, -32769, Integer.MIN_VALUE, Integer.MIN_VALUE - 1L, Long.MIN_VALUE})
	void integersRoundTripAsLong(long value) {
		assertEquals(value, roundTrip(value));
	}

	@Test
	void integersUseSmallestEncoding() {
		assertArrayEquals(bytes(0x7f), encode(127));
		assertArrayEquals(bytes(0xcc, 0x80), encode(128));
		assertArrayEquals(bytes(0xcd, 0x01, 0x00), encode(256));
		assertArrayEquals(bytes(0xce, 0x00, 0x01, 0x00, 0x00), encode(65536));
		assertArrayEquals(bytes(0xff), encode(-1));
		assertArrayEquals(bytes(0xe0), encode(-32));
		assertArrayEquals(bytes(0xd0, 0xdf), encode(-33));
		assertArrayEquals(bytes(0xd1, 0xff, 0x7f), encode(-129));
		assertEquals(9, encode(Long.MIN_VALUE).length);
	}

	@Test
	void everyIntegerBoxTypeDecodesToLong() {
		assertEquals(5L, roundTrip((byte) 5));
		assertEquals(5L, roundTrip((short) 5));
		assertEquals(5L, roundTrip(5));
		assertEquals(5L, roundTrip(BigInteger.valueOf(5)));
	}

	@Test
	void floatsDecodeToDouble() {
		assertEquals(1.5, roundTrip(1.5f));
		assertEquals(5, encode(1.5f).length);
		assertEquals(Math.PI, roundTrip(Math.PI));
		assertEquals(Double.NaN, roundTrip(Double.NaN));
		assertEquals(Double.NEGATIVE_INFINITY, roundTrip(Double.NEGATIVE_INFINITY));
	}

	@Test
	void nilAndBooleans() {
		assertNull(roundTrip(null));
		assertEquals(true, roundTrip(true));
		assertEquals(false, roundTrip(false));
		assertArrayEquals(bytes(0xc0), encode(null));
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 1, 31, 32, 255, 256, 65535, 65536, 100_000})
	void stringsOfEveryHeaderWidth(int length) {
		String value = "x".repeat(length);
		assertEquals(value, roundTrip(value));
	}

	@Test
	void unicodeStrings() {
		String value = "héllo wörld — 日本語 🎮🧱";
		assertEquals(value, roundTrip(value));
		assertArrayEquals(value.getBytes(java.nio.charset.StandardCharsets.UTF_8),
			Arrays.copyOfRange(encode(value), 2, encode(value).length));
	}

	@Test
	void unpairedSurrogateBecomesQuestionMark() {
		assertEquals("a?b", roundTrip("a\uD800b"));
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 1, 255, 256, 65535, 65536})
	void binaryOfEveryHeaderWidth(int length) {
		byte[] value = new byte[length];
		Arrays.fill(value, (byte) 7);
		assertArrayEquals(value, (byte[]) roundTrip(value));
	}

	@Test
	void byteBufferEncodesRemainingBytesAsBinary() {
		ByteBuffer buffer = ByteBuffer.wrap(bytes(1, 2, 3, 4));
		buffer.position(1);
		assertArrayEquals(bytes(2, 3, 4), (byte[]) roundTrip(buffer));
		assertEquals(1, buffer.position());
	}

	@ParameterizedTest
	@ValueSource(ints = {0, 15, 16, 65535, 65536})
	void arraysAndMapsOfEveryHeaderWidth(int size) {
		List<Object> list = new ArrayList<>();
		Map<String, Object> map = new HashMap<>();
		for (int i = 0; i < size; i++) {
			list.add((long) i);
			map.put("k" + i, (long) i);
		}
		assertEquals(list, roundTrip(list));
		assertEquals(map, roundTrip(map));
	}

	@Test
	void nestedStructures() {
		Map<String, Object> value = new LinkedHashMap<>();
		value.put("list", List.of(1L, "two", List.of(3.0), Map.of("four", true)));
		value.put("nil", null);
		value.put("bin", bytes(9));
		@SuppressWarnings("unchecked")
		Map<String, Object> decoded = (Map<String, Object>) roundTrip(value);
		assertEquals(value.get("list"), decoded.get("list"));
		assertTrue(decoded.containsKey("nil"));
		assertArrayEquals(bytes(9), (byte[]) decoded.get("bin"));
	}

	@Test
	void primitiveArraysAndObjectArraysBecomeLists() {
		assertEquals(List.of(1L, 2L), roundTrip(new int[] {1, 2}));
		assertEquals(List.of(1L, 2L), roundTrip(new long[] {1, 2}));
		assertEquals(List.of(0.5, 1.5), roundTrip(new float[] {0.5f, 1.5f}));
		assertEquals(List.of(0.5), roundTrip(new double[] {0.5}));
		assertEquals(List.of("a", 1L), roundTrip(new Object[] {"a", 1}));
	}

	@Test
	void enumsAndNonStringKeysBecomeStrings() {
		assertEquals("SECONDS", roundTrip(java.util.concurrent.TimeUnit.SECONDS));
		assertEquals(Map.of("1", "x"), roundTrip(Map.of(1, "x")));
	}

	@Test
	void unsupportedValueIsRejected() {
		assertThrows(MsgPackException.class, () -> encode(new Object()));
	}

	@Test
	void cyclicStructureIsRejected() {
		List<Object> list = new ArrayList<>();
		list.add(list);
		assertThrows(MsgPackException.class, () -> encode(list));
	}

	@Test
	void uint64AboveLongMaxIsRejected() {
		byte[] max = bytes(0xcf, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff);
		assertThrows(MsgPackException.class, () -> MsgPackReader.decode(max));
		assertEquals(Long.MAX_VALUE, MsgPackReader.decode(bytes(0xcf, 0x7f, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff)));
	}

	@Test
	void bigUnsigned64EncodesAsUint64() {
		BigInteger value = BigInteger.ONE.shiftLeft(63);
		assertEquals(0xcf, encode(value)[0] & 0xff);
		assertThrows(MsgPackException.class, () -> encode(BigInteger.ONE.shiftLeft(64)));
	}

	@Test
	void decodesForeignEncodings() {
		assertEquals(1.0, MsgPackReader.decode(bytes(0xca, 0x3f, 0x80, 0x00, 0x00)));
		assertEquals(-2L, MsgPackReader.decode(bytes(0xd3, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xff, 0xfe)));
		assertEquals("", MsgPackReader.decode(bytes(0xd9, 0x00)));
		assertEquals(List.of(), MsgPackReader.decode(bytes(0xdc, 0x00, 0x00)));
		assertEquals(Map.of(), MsgPackReader.decode(bytes(0xdf, 0, 0, 0, 0)));
	}

	@Test
	void malformedInputIsRejected() {
		assertThrows(MsgPackException.class, () -> MsgPackReader.decode(bytes(0xc1)));
		assertThrows(MsgPackException.class, () -> MsgPackReader.decode(bytes(0xd4, 0x01, 0x00)));
		assertThrows(MsgPackException.class, () -> MsgPackReader.decode(bytes(0xa5, 'a')));
		assertThrows(MsgPackException.class, () -> MsgPackReader.decode(bytes(0xdd, 0x7f, 0xff, 0xff, 0xff)));
		assertThrows(MsgPackException.class, () -> MsgPackReader.decode(bytes(0x81, 0x01, 0x02)));
		assertThrows(MsgPackException.class, () -> MsgPackReader.decode(bytes(0x01, 0x02)));
		assertThrows(MsgPackException.class, () -> MsgPackReader.decode(new byte[0]));
	}

	@Test
	void writerCanBeReusedAfterReset() {
		MsgPackWriter writer = new MsgPackWriter(16);
		writer.writeValue("x".repeat(1000));
		writer.reset();
		writer.writeValue(1);
		assertArrayEquals(bytes(1), writer.toByteArray());
	}
}
