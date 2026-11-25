package mod.kelvinlby.opencrafter.transport.msgpack;

import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import static mod.kelvinlby.opencrafter.transport.msgpack.MsgPackFormat.*;

/**
 * Allocation-light MessagePack encoder into a growable byte array. Not thread-safe; reuse
 * one instance per thread via {@link #reset()}.
 *
 * <p>Supported values: {@code null}, {@link Boolean}, integral {@link Number}s (smallest
 * encoding), {@link Float} (float32), {@link Double} and other numbers (float64),
 * {@link CharSequence}, {@link Character}, {@link Enum} (its name), {@code byte[]} and
 * {@link ByteBuffer} (bin), {@link Map} (keys via {@code String.valueOf}),
 * {@link Collection}, {@code Object[]} and primitive arrays (array).
 */
public final class MsgPackWriter {
	private static final int MAX_CAPACITY = Integer.MAX_VALUE - 16;
	private static final BigInteger UINT64_LIMIT = BigInteger.ONE.shiftLeft(64);

	private byte[] buffer;
	private int position;

	public MsgPackWriter(int initialCapacity) {
		buffer = new byte[Math.max(16, initialCapacity)];
	}

	public void reset() {
		position = 0;
	}

	public int size() {
		return position;
	}

	public int capacity() {
		return buffer.length;
	}

	public byte[] toByteArray() {
		return Arrays.copyOf(buffer, position);
	}

	/** Reserves {@code count} bytes (e.g. a length prefix) and returns their offset. */
	public int reserve(int count) {
		ensure(count);
		int offset = position;
		position += count;
		return offset;
	}

	/** Overwrites four bytes at {@code offset} with {@code value}, little-endian. */
	public void patchIntLE(int offset, int value) {
		buffer[offset] = (byte) value;
		buffer[offset + 1] = (byte) (value >>> 8);
		buffer[offset + 2] = (byte) (value >>> 16);
		buffer[offset + 3] = (byte) (value >>> 24);
	}

	public MsgPackWriter writeValue(Object value) {
		writeValue(value, 0);
		return this;
	}

	public MsgPackWriter writeNil() {
		writeByte(NIL);
		return this;
	}

	public MsgPackWriter writeBool(boolean value) {
		writeByte(value ? TRUE : FALSE);
		return this;
	}

	public MsgPackWriter writeInt(long value) {
		if (value >= 0) {
			writeUnsigned(value);
		} else {
			writeNegative(value);
		}
		return this;
	}

	public MsgPackWriter writeFloat(float value) {
		writeByte(FLOAT32);
		writeInt32(Float.floatToRawIntBits(value));
		return this;
	}

	public MsgPackWriter writeDouble(double value) {
		writeByte(FLOAT64);
		writeInt64(Double.doubleToRawLongBits(value));
		return this;
	}

	public MsgPackWriter writeString(CharSequence value) {
		int length = utf8Length(value);
		writeStringHeader(length);
		writeUtf8(value, length);
		return this;
	}

	public MsgPackWriter writeBinary(byte[] value, int offset, int length) {
		writeBinaryHeader(length);
		ensure(length);
		System.arraycopy(value, offset, buffer, position, length);
		position += length;
		return this;
	}

	public MsgPackWriter writeBinary(ByteBuffer value) {
		ByteBuffer source = value.duplicate();
		int length = source.remaining();
		writeBinaryHeader(length);
		ensure(length);
		source.get(buffer, position, length);
		position += length;
		return this;
	}

	public MsgPackWriter writeArrayHeader(int size) {
		if (size <= FIX_COLLECTION_MAX) {
			writeByte(FIXARRAY | size);
		} else if (size <= 0xffff) {
			writeByte(ARRAY16);
			writeInt16(size);
		} else {
			writeByte(ARRAY32);
			writeInt32(size);
		}
		return this;
	}

	public MsgPackWriter writeMapHeader(int size) {
		if (size <= FIX_COLLECTION_MAX) {
			writeByte(FIXMAP | size);
		} else if (size <= 0xffff) {
			writeByte(MAP16);
			writeInt16(size);
		} else {
			writeByte(MAP32);
			writeInt32(size);
		}
		return this;
	}

	private void writeValue(Object value, int depth) {
		if (depth > MAX_DEPTH) {
			throw new MsgPackException("Value nested deeper than " + MAX_DEPTH);
		}
		switch (value) {
			case null -> writeNil();
			case Boolean bool -> writeBool(bool);
			case Long number -> writeInt(number);
			case Integer number -> writeInt(number);
			case Short number -> writeInt(number);
			case Byte number -> writeInt(number);
			case Float number -> writeFloat(number);
			case Double number -> writeDouble(number);
			case BigInteger number -> writeBigInteger(number);
			case Number number -> writeDouble(number.doubleValue());
			case CharSequence text -> writeString(text);
			case Character character -> writeString(String.valueOf(character));
			case Enum<?> constant -> writeString(constant.name());
			case byte[] bytes -> writeBinary(bytes, 0, bytes.length);
			case ByteBuffer bytes -> writeBinary(bytes);
			case Map<?, ?> map -> writeMap(map, depth);
			case Collection<?> collection -> writeCollection(collection, depth);
			case Object[] array -> writeObjectArray(array, depth);
			case int[] array -> writeIntArray(array);
			case long[] array -> writeLongArray(array);
			case float[] array -> writeFloatArray(array);
			case double[] array -> writeDoubleArray(array);
			default -> throw new MsgPackException("Cannot encode " + value.getClass().getName());
		}
	}

	private void writeMap(Map<?, ?> map, int depth) {
		writeMapHeader(map.size());
		for (Map.Entry<?, ?> entry : map.entrySet()) {
			writeString(String.valueOf(entry.getKey()));
			writeValue(entry.getValue(), depth + 1);
		}
	}

	private void writeCollection(Collection<?> collection, int depth) {
		writeArrayHeader(collection.size());
		for (Object element : collection) {
			writeValue(element, depth + 1);
		}
	}

	private void writeObjectArray(Object[] array, int depth) {
		writeArrayHeader(array.length);
		for (Object element : array) {
			writeValue(element, depth + 1);
		}
	}

	private void writeIntArray(int[] array) {
		writeArrayHeader(array.length);
		for (int element : array) {
			writeInt(element);
		}
	}

	private void writeLongArray(long[] array) {
		writeArrayHeader(array.length);
		for (long element : array) {
			writeInt(element);
		}
	}

	private void writeFloatArray(float[] array) {
		writeArrayHeader(array.length);
		for (float element : array) {
			writeFloat(element);
		}
	}

	private void writeDoubleArray(double[] array) {
		writeArrayHeader(array.length);
		for (double element : array) {
			writeDouble(element);
		}
	}

	private void writeBigInteger(BigInteger value) {
		if (value.bitLength() < 64) {
			writeInt(value.longValue());
		} else if (value.signum() > 0 && value.compareTo(UINT64_LIMIT) < 0) {
			writeByte(UINT64);
			writeInt64(value.longValue());
		} else {
			throw new MsgPackException("Integer out of 64-bit range: " + value);
		}
	}

	private void writeUnsigned(long value) {
		if (value <= POSITIVE_FIXINT_MAX) {
			writeByte((int) value);
		} else if (value <= 0xffL) {
			writeByte(UINT8);
			writeByte((int) value);
		} else if (value <= 0xffffL) {
			writeByte(UINT16);
			writeInt16((int) value);
		} else if (value <= 0xffffffffL) {
			writeByte(UINT32);
			writeInt32((int) value);
		} else {
			writeByte(UINT64);
			writeInt64(value);
		}
	}

	private void writeNegative(long value) {
		if (value >= -32) {
			writeByte((int) value & 0xff);
		} else if (value >= Byte.MIN_VALUE) {
			writeByte(INT8);
			writeByte((int) value);
		} else if (value >= Short.MIN_VALUE) {
			writeByte(INT16);
			writeInt16((int) value);
		} else if (value >= Integer.MIN_VALUE) {
			writeByte(INT32);
			writeInt32((int) value);
		} else {
			writeByte(INT64);
			writeInt64(value);
		}
	}

	private void writeStringHeader(int length) {
		if (length <= FIXSTR_MAX) {
			writeByte(FIXSTR | length);
		} else if (length <= 0xff) {
			writeByte(STR8);
			writeByte(length);
		} else if (length <= 0xffff) {
			writeByte(STR16);
			writeInt16(length);
		} else {
			writeByte(STR32);
			writeInt32(length);
		}
	}

	private void writeBinaryHeader(int length) {
		if (length <= 0xff) {
			writeByte(BIN8);
			writeByte(length);
		} else if (length <= 0xffff) {
			writeByte(BIN16);
			writeInt16(length);
		} else {
			writeByte(BIN32);
			writeInt32(length);
		}
	}

	/** UTF-8 byte length; unpaired surrogates count as one byte ('?'). */
	private static int utf8Length(CharSequence value) {
		int length = value.length();
		int bytes = 0;
		for (int i = 0; i < length; i++) {
			char c = value.charAt(i);
			if (c < 0x80 || isSurrogate(c) && !isSurrogatePair(value, i)) {
				bytes += 1;
			} else if (c < 0x800) {
				bytes += 2;
			} else if (isSurrogatePair(value, i)) {
				bytes += 4;
				i++;
			} else {
				bytes += 3;
			}
		}
		return bytes;
	}

	private void writeUtf8(CharSequence value, int byteLength) {
		ensure(byteLength);
		byte[] out = buffer;
		int at = position;
		int length = value.length();
		for (int i = 0; i < length; i++) {
			char c = value.charAt(i);
			if (c < 0x80) {
				out[at++] = (byte) c;
			} else if (c < 0x800) {
				out[at++] = (byte) (0xc0 | c >> 6);
				out[at++] = (byte) (0x80 | c & 0x3f);
			} else if (isSurrogatePair(value, i)) {
				int codePoint = Character.toCodePoint(c, value.charAt(++i));
				out[at++] = (byte) (0xf0 | codePoint >> 18);
				out[at++] = (byte) (0x80 | codePoint >> 12 & 0x3f);
				out[at++] = (byte) (0x80 | codePoint >> 6 & 0x3f);
				out[at++] = (byte) (0x80 | codePoint & 0x3f);
			} else if (isSurrogate(c)) {
				out[at++] = '?';
			} else {
				out[at++] = (byte) (0xe0 | c >> 12);
				out[at++] = (byte) (0x80 | c >> 6 & 0x3f);
				out[at++] = (byte) (0x80 | c & 0x3f);
			}
		}
		position = at;
	}

	private static boolean isSurrogate(char c) {
		return Character.isSurrogate(c);
	}

	private static boolean isSurrogatePair(CharSequence value, int index) {
		return Character.isHighSurrogate(value.charAt(index))
			&& index + 1 < value.length()
			&& Character.isLowSurrogate(value.charAt(index + 1));
	}

	private void writeByte(int value) {
		ensure(1);
		buffer[position++] = (byte) value;
	}

	private void writeInt16(int value) {
		ensure(2);
		buffer[position++] = (byte) (value >>> 8);
		buffer[position++] = (byte) value;
	}

	private void writeInt32(int value) {
		ensure(4);
		buffer[position++] = (byte) (value >>> 24);
		buffer[position++] = (byte) (value >>> 16);
		buffer[position++] = (byte) (value >>> 8);
		buffer[position++] = (byte) value;
	}

	private void writeInt64(long value) {
		writeInt32((int) (value >>> 32));
		writeInt32((int) value);
	}

	private void ensure(int extra) {
		long required = (long) position + extra;
		if (required > buffer.length) {
			grow(required);
		}
	}

	private void grow(long required) {
		if (required > MAX_CAPACITY) {
			throw new MsgPackException("Encoded message too large");
		}
		long doubled = Math.max(required, (long) buffer.length * 2);
		buffer = Arrays.copyOf(buffer, (int) Math.min(doubled, MAX_CAPACITY));
	}
}
