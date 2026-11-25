package mod.kelvinlby.opencrafter.transport.msgpack;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static mod.kelvinlby.opencrafter.transport.msgpack.MsgPackFormat.*;

/**
 * MessagePack decoder over a byte range. Not thread-safe; reuse via {@link #reset}.
 *
 * <p>Decoded types: nil → {@code null}, bool → {@link Boolean}, every integer width →
 * {@link Long} (uint64 above {@code Long.MAX_VALUE} is rejected), float32/float64 →
 * {@link Double}, str → {@link String}, bin → {@code byte[]}, array →
 * {@code List<Object>}, map → {@code Map<String, Object>} (keys must be strings).
 * Extension types are rejected.
 */
public final class MsgPackReader {
	private byte[] buffer;
	private int position;
	private int limit;

	public MsgPackReader() {
		this(new byte[0], 0, 0);
	}

	public MsgPackReader(byte[] buffer, int offset, int length) {
		reset(buffer, offset, length);
	}

	/** Decodes exactly one value occupying the whole range. */
	public static Object decode(byte[] buffer, int offset, int length) {
		MsgPackReader reader = new MsgPackReader(buffer, offset, length);
		Object value = reader.readValue();
		reader.requireFullyConsumed();
		return value;
	}

	public static Object decode(byte[] buffer) {
		return decode(buffer, 0, buffer.length);
	}

	public void reset(byte[] buffer, int offset, int length) {
		if (offset < 0 || length < 0 || offset + length > buffer.length) {
			throw new IndexOutOfBoundsException("Range outside buffer");
		}
		this.buffer = buffer;
		this.position = offset;
		this.limit = offset + length;
	}

	public boolean hasRemaining() {
		return position < limit;
	}

	public void requireFullyConsumed() {
		if (hasRemaining()) {
			throw new MsgPackException("Trailing bytes after value: " + (limit - position));
		}
	}

	public Object readValue() {
		return readValue(0);
	}

	private Object readValue(int depth) {
		if (depth > MAX_DEPTH) {
			throw new MsgPackException("Value nested deeper than " + MAX_DEPTH);
		}
		int type = readUnsignedByte();
		if (type <= POSITIVE_FIXINT_MAX) {
			return (long) type;
		}
		if (type >= NEGATIVE_FIXINT) {
			return (long) (byte) type;
		}
		if (type < FIXARRAY) {
			return readMap(type & 0x0f, depth);
		}
		if (type < FIXSTR) {
			return readArray(type & 0x0f, depth);
		}
		if (type < NIL) {
			return readString(type & 0x1f);
		}
		return readTagged(type, depth);
	}

	private Object readTagged(int type, int depth) {
		return switch (type) {
			case NIL -> null;
			case FALSE -> Boolean.FALSE;
			case TRUE -> Boolean.TRUE;
			case BIN8 -> readBinary(readUnsignedByte());
			case BIN16 -> readBinary(readUnsignedShort());
			case BIN32 -> readBinary(readLength32());
			case FLOAT32 -> (double) Float.intBitsToFloat(readInt32());
			case FLOAT64 -> Double.longBitsToDouble(readInt64());
			case UINT8 -> (long) readUnsignedByte();
			case UINT16 -> (long) readUnsignedShort();
			case UINT32 -> readInt32() & 0xffffffffL;
			case UINT64 -> readUnsigned64();
			case INT8 -> (long) (byte) readUnsignedByte();
			case INT16 -> (long) (short) readUnsignedShort();
			case INT32 -> (long) readInt32();
			case INT64 -> readInt64();
			case STR8 -> readString(readUnsignedByte());
			case STR16 -> readString(readUnsignedShort());
			case STR32 -> readString(readLength32());
			case ARRAY16 -> readArray(readUnsignedShort(), depth);
			case ARRAY32 -> readArray(readLength32(), depth);
			case MAP16 -> readMap(readUnsignedShort(), depth);
			case MAP32 -> readMap(readLength32(), depth);
			default -> throw unsupported(type);
		};
	}

	private static MsgPackException unsupported(int type) {
		boolean extension = type == EXT8 || type == EXT16 || type == EXT32 || type >= FIXEXT1 && type <= FIXEXT16;
		String what = extension ? "Extension types are not supported" : "Invalid type byte";
		return new MsgPackException(what + String.format(" (0x%02x)", type));
	}

	private List<Object> readArray(int size, int depth) {
		requireElements(size);
		List<Object> list = new ArrayList<>(size);
		for (int i = 0; i < size; i++) {
			list.add(readValue(depth + 1));
		}
		return list;
	}

	private Map<String, Object> readMap(int size, int depth) {
		requireElements(size * 2L);
		Map<String, Object> map = HashMap.newHashMap(size);
		for (int i = 0; i < size; i++) {
			if (!(readValue(depth + 1) instanceof String key)) {
				throw new MsgPackException("Map keys must be strings");
			}
			map.put(key, readValue(depth + 1));
		}
		return map;
	}

	private String readString(int length) {
		require(length);
		String value = new String(buffer, position, length, StandardCharsets.UTF_8);
		position += length;
		return value;
	}

	private byte[] readBinary(int length) {
		require(length);
		byte[] value = Arrays.copyOfRange(buffer, position, position + length);
		position += length;
		return value;
	}

	private long readUnsigned64() {
		long value = readInt64();
		if (value < 0) {
			throw new MsgPackException("uint64 value exceeds Long.MAX_VALUE");
		}
		return value;
	}

	/** A 32-bit length; every element needs at least one byte, which bounds it by the input. */
	private int readLength32() {
		long length = readInt32() & 0xffffffffL;
		if (length > limit - position) {
			throw truncated();
		}
		return (int) length;
	}

	private void requireElements(long minimumBytes) {
		if (minimumBytes > limit - position) {
			throw truncated();
		}
	}

	private int readUnsignedByte() {
		require(1);
		return buffer[position++] & 0xff;
	}

	private int readUnsignedShort() {
		require(2);
		int value = (buffer[position] & 0xff) << 8 | buffer[position + 1] & 0xff;
		position += 2;
		return value;
	}

	private int readInt32() {
		require(4);
		byte[] b = buffer;
		int p = position;
		position = p + 4;
		return (b[p] & 0xff) << 24 | (b[p + 1] & 0xff) << 16 | (b[p + 2] & 0xff) << 8 | b[p + 3] & 0xff;
	}

	private long readInt64() {
		long high = readInt32() & 0xffffffffL;
		long low = readInt32() & 0xffffffffL;
		return high << 32 | low;
	}

	private void require(int count) {
		if (count > limit - position) {
			throw truncated();
		}
	}

	private static MsgPackException truncated() {
		return new MsgPackException("Truncated MessagePack data");
	}
}
