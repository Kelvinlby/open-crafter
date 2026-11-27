package mod.kelvinlby.opencrafter.encoder.output;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Turns one frame of a vision stream into bytes that compress well. Float fields (optical flow)
 * are smooth, so each channel becomes its own plane, every element is replaced by its difference
 * to its left neighbour (on the integer bit pattern, so the transform is exact and reversible),
 * and the bytes are shuffled into planes (all low bytes, then all high bytes). On real flow this
 * compresses about 3.5× better than shuffling alone.
 */
public sealed interface ArrayCodec {
	byte[] pack(ByteBuffer frame, int width, int height);

	int channels();

	/** Name stored in the stream header; readers dispatch on it. */
	String encoding();

	/** numpy dtype of the decoded elements. */
	String decodedDtype();

	/** Decoded value = stored / scale (1 for everything but fixed point). */
	default double scale() {
		return 1.0;
	}

	static ArrayCodec forFlow(String precision, double q16Scale) {
		return switch (precision) {
			case "f16" -> new HalfFloat(2);
			case "f32" -> new Float32(2);
			case "q16" -> new Fixed16(2, q16Scale);
			default -> throw new IllegalArgumentException("Unknown flow precision: " + precision + " (f16, f32, q16)");
		};
	}

	/** float32 → IEEE half (round to nearest even), planar, horizontal delta, 2 byte planes. */
	record HalfFloat(int channels) implements ArrayCodec {
		@Override
		public byte[] pack(ByteBuffer frame, int width, int height) {
			return Planes.deltaShuffled16(frame, width, height, channels, Float::floatToFloat16);
		}

		@Override
		public String encoding() {
			return "f16-delta";
		}

		@Override
		public String decodedDtype() {
			return "float16";
		}
	}

	/** float32 → int16 fixed point (value × scale, saturating), planar, horizontal delta, 2 byte planes. */
	record Fixed16(int channels, double scale) implements ArrayCodec {
		@Override
		public byte[] pack(ByteBuffer frame, int width, int height) {
			return Planes.deltaShuffled16(frame, width, height, channels, v -> (short) Math.clamp(Math.round(v * scale), Short.MIN_VALUE, Short.MAX_VALUE));
		}

		@Override
		public String encoding() {
			return "q16-delta";
		}

		@Override
		public String decodedDtype() {
			return "int16";
		}
	}

	/** float32 kept exactly: planar, horizontal delta of the bit patterns, 4 byte planes. */
	record Float32(int channels) implements ArrayCodec {
		@Override
		public byte[] pack(ByteBuffer frame, int width, int height) {
			return Planes.deltaShuffled32(frame, width, height, channels);
		}

		@Override
		public String encoding() {
			return "f32-delta";
		}

		@Override
		public String decodedDtype() {
			return "float32";
		}
	}

	/** 0/1 bytes → bits, most significant bit first (numpy {@code packbits} order). */
	record Bits() implements ArrayCodec {
		@Override
		public byte[] pack(ByteBuffer frame, int width, int height) {
			int n = frame.remaining();
			byte[] out = new byte[(n + 7) / 8];
			for (int i = 0; i < n; i++) {
				if (frame.get() != 0) {
					out[i >>> 3] |= (byte) (0x80 >>> (i & 7));
				}
			}
			return out;
		}

		@Override
		public int channels() {
			return 1;
		}

		@Override
		public String encoding() {
			return "bits";
		}

		@Override
		public String decodedDtype() {
			return "bool";
		}
	}

	/** The plane/delta/shuffle transforms shared by the float codecs. */
	final class Planes {
		@FunctionalInterface
		interface FloatTo16 {
			short convert(float value);
		}

		private Planes() {
		}

		/** Output: plane c, byte b, row-major element i at {@code (c * 2 + b) * n + i}, n = width × height. */
		static byte[] deltaShuffled16(ByteBuffer frame, int width, int height, int channels, FloatTo16 convert) {
			ByteBuffer in = frame.order(ByteOrder.LITTLE_ENDIAN);
			int n = width * height;
			byte[] out = new byte[n * channels * 2];
			short[] previous = new short[channels];
			for (int i = 0; i < n; i++) {
				boolean rowStart = i % width == 0;
				for (int c = 0; c < channels; c++) {
					short value = convert.convert(in.getFloat());
					short delta = (short) (rowStart ? value : value - previous[c]);
					previous[c] = value;
					out[(c * 2) * n + i] = (byte) delta;
					out[(c * 2 + 1) * n + i] = (byte) (delta >>> 8);
				}
			}
			return out;
		}

		static byte[] deltaShuffled32(ByteBuffer frame, int width, int height, int channels) {
			ByteBuffer in = frame.order(ByteOrder.LITTLE_ENDIAN);
			int n = width * height;
			byte[] out = new byte[n * channels * 4];
			int[] previous = new int[channels];
			for (int i = 0; i < n; i++) {
				boolean rowStart = i % width == 0;
				for (int c = 0; c < channels; c++) {
					int value = Float.floatToRawIntBits(in.getFloat());
					int delta = rowStart ? value : value - previous[c];
					previous[c] = value;
					for (int b = 0; b < 4; b++) {
						out[(c * 4 + b) * n + i] = (byte) (delta >>> (8 * b));
					}
				}
			}
			return out;
		}
	}
}
