package mod.kelvinlby.opencrafter.encoder.format;

import java.io.ByteArrayOutputStream;

/** Unsigned LEB128 varints, as the container and the Minecraft protocol use them. */
public final class VarInts {
	private VarInts() {
	}

	/** Reads a varint at {@code offset}; -1 if it is malformed or runs off the end. */
	public static int peekVarInt(byte[] data, int offset) {
		int result = 0;
		for (int i = 0; i < 5 && offset + i < data.length; i++) {
			int b = data[offset + i] & 0xFF;
			result |= (b & 0x7F) << (7 * i);
			if ((b & 0x80) == 0) {
				return result;
			}
		}
		return -1;
	}

	public static void writeVarInt(ByteArrayOutputStream out, int value) {
		writeVarLong(out, value & 0xFFFFFFFFL);
	}

	public static void writeVarLong(ByteArrayOutputStream out, long value) {
		while ((value & ~0x7FL) != 0) {
			out.write((int) (value & 0x7F) | 0x80);
			value >>>= 7;
		}
		out.write((int) value);
	}
}
