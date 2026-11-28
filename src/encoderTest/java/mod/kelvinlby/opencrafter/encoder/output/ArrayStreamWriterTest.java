package mod.kelvinlby.opencrafter.encoder.output;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Writes one stream per codec plus the raw frames they came from into {@code streams/}; the Python
 * reader's test ({@code python/tests/test_episode_streams.py}) decodes them and compares.
 */
class ArrayStreamWriterTest {
	private static final int WIDTH = 37;
	private static final int HEIGHT = 11;
	private static final int FRAMES = 3;

	@Test
	void writesEveryCodecAndTheReferenceFrames() throws IOException {
		Path dir = Files.createDirectories(Path.of("streams"));
		writeStream(dir.resolve("f16.ocs"), new ArrayCodec.HalfFloat(2));
		writeStream(dir.resolve("f32.ocs"), new ArrayCodec.Float32(2));
		writeStream(dir.resolve("q16.ocs"), new ArrayCodec.Fixed16(2, 16.0));
		try (ArrayStreamWriter writer = new ArrayStreamWriter(dir.resolve("bits.ocs"), "bits", WIDTH, HEIGHT, new ArrayCodec.Bits(), 3)) {
			for (int f = 0; f < FRAMES; f++) {
				writer.write(maskFrame(f));
			}
		}
		for (int f = 0; f < FRAMES; f++) {
			Files.write(dir.resolve("flow-" + f + ".f32"), toBytes(flowFrame(f)));
			Files.write(dir.resolve("mask-" + f + ".u8"), toBytes(maskFrame(f)));
		}
		assertTrue(Files.size(dir.resolve("f32.ocs")) > 0);
	}

	@Test
	void deltaTransformIsExactlyReversible() {
		ByteBuffer frame = flowFrame(1);
		byte[] packed = new ArrayCodec.Float32(2).pack(frame.duplicate(), WIDTH, HEIGHT);
		int n = WIDTH * HEIGHT;
		FloatPlaneReader reader = new FloatPlaneReader(packed, n);
		for (int i = 0; i < n; i++) {
			for (int c = 0; c < 2; c++) {
				assertEquals(frame.getFloat((i * 2 + c) * 4), reader.value(c, i, WIDTH));
			}
		}
	}

	@Test
	void halfFloatPlanesFollowTheDocumentedLayout() {
		ByteBuffer frame = flowFrame(2);
		byte[] packed = new ArrayCodec.HalfFloat(2).pack(frame.duplicate(), WIDTH, HEIGHT);
		int n = WIDTH * HEIGHT;
		for (int i = 0; i < n; i++) {
			for (int c = 0; c < 2; c++) {
				short value = Float.floatToFloat16(frame.getFloat((i * 2 + c) * 4));
				short left = i % WIDTH == 0 ? 0 : Float.floatToFloat16(frame.getFloat(((i - 1) * 2 + c) * 4));
				short delta = (short) (value - left);
				assertEquals((byte) delta, packed[(c * 2) * n + i]);
				assertEquals((byte) (delta >>> 8), packed[(c * 2 + 1) * n + i]);
			}
		}
	}

	@Test
	void bitsArePackedMostSignificantFirst() {
		ByteBuffer frame = maskFrame(1);
		byte[] packed = new ArrayCodec.Bits().pack(frame.duplicate(), WIDTH, HEIGHT);
		for (int i = 0; i < WIDTH * HEIGHT; i++) {
			boolean bit = (packed[i >>> 3] & (0x80 >>> (i & 7))) != 0;
			assertEquals(frame.get(i) != 0, bit);
		}
	}

	private static void writeStream(Path file, ArrayCodec codec) throws IOException {
		try (ArrayStreamWriter writer = new ArrayStreamWriter(file, "flow", WIDTH, HEIGHT, codec, 3)) {
			for (int f = 0; f < FRAMES; f++) {
				writer.write(flowFrame(f));
			}
		}
	}

	/** A smooth field with a discontinuity and negative values, like real flow. */
	static ByteBuffer flowFrame(int frame) {
		ByteBuffer buffer = ByteBuffer.allocate(WIDTH * HEIGHT * 2 * 4).order(ByteOrder.LITTLE_ENDIAN);
		for (int y = 0; y < HEIGHT; y++) {
			for (int x = 0; x < WIDTH; x++) {
				float u = (float) (Math.sin(x * 0.3 + frame) * 12.5 + (x > WIDTH / 2 ? 40 : 0));
				float v = (float) (Math.cos(y * 0.7 - frame) * -3.25 - x * 0.01);
				buffer.putFloat(u).putFloat(v);
			}
		}
		return buffer.flip();
	}

	static ByteBuffer maskFrame(int frame) {
		ByteBuffer buffer = ByteBuffer.allocate(WIDTH * HEIGHT);
		for (int i = 0; i < WIDTH * HEIGHT; i++) {
			buffer.put((byte) ((i * 7 + frame) % 5 == 0 ? 1 : 0));
		}
		return buffer.flip();
	}

	private static byte[] toBytes(ByteBuffer buffer) {
		byte[] bytes = new byte[buffer.remaining()];
		buffer.duplicate().get(bytes);
		return bytes;
	}

	/** Undoes the f32 planar/delta/shuffle layout for one element. */
	private record FloatPlaneReader(byte[] packed, int n) {
		float value(int channel, int index, int width) {
			int rowStart = index - index % width;
			int sum = 0;
			for (int i = rowStart; i <= index; i++) {
				int delta = 0;
				for (int b = 0; b < 4; b++) {
					delta |= (packed[(channel * 4 + b) * n + i] & 0xFF) << (8 * b);
				}
				sum += delta;
			}
			return Float.intBitsToFloat(sum);
		}
	}
}
