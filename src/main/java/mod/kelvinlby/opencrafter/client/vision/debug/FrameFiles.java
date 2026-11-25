package mod.kelvinlby.opencrafter.client.vision.debug;

import mod.kelvinlby.opencrafter.OpenCrafter;
import mod.kelvinlby.opencrafter.api.frame.DType;
import mod.kelvinlby.opencrafter.api.frame.StreamSpec;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Writes one debug frame: u8 streams as PNG, f32 streams as little-endian .npy, meta as text. */
final class FrameFiles {
	private FrameFiles() {
	}

	static void write(Path directory, long frameIndex, List<StreamSpec> specs, Map<String, ByteBuffer> buffers, Map<String, Object> meta) {
		try {
			Files.createDirectories(directory);
			String prefix = String.format(Locale.ROOT, "%06d_", frameIndex);
			for (StreamSpec spec : specs) {
				ByteBuffer data = buffers.get(spec.name()).rewind();
				Path base = directory.resolve(prefix + spec.name());
				if (spec.dtype() == DType.UINT8) {
					writePng(base.resolveSibling(base.getFileName() + ".png"), spec, data);
				} else {
					writeNpy(base.resolveSibling(base.getFileName() + ".npy"), spec, data);
				}
			}
			Files.writeString(directory.resolve(prefix + "meta.txt"), meta.toString());
		} catch (IOException | RuntimeException e) {
			OpenCrafter.LOGGER.error("Vision dump of frame {} failed", frameIndex, e);
		}
	}

	private static void writePng(Path file, StreamSpec spec, ByteBuffer data) throws IOException {
		BufferedImage image = new BufferedImage(spec.width(), spec.height(), BufferedImage.TYPE_INT_RGB);
		for (int y = 0; y < spec.height(); y++) {
			for (int x = 0; x < spec.width(); x++) {
				image.setRGB(x, y, pixelRgb(spec, data, (y * spec.width() + x) * spec.channels()));
			}
		}
		ImageIO.write(image, "png", file.toFile());
	}

	/** RGB(A) streams keep their color; single-channel streams are scaled so value 1 is white. */
	private static int pixelRgb(StreamSpec spec, ByteBuffer data, int offset) {
		if (spec.channels() >= 3) {
			return (data.get(offset) & 0xFF) << 16 | (data.get(offset + 1) & 0xFF) << 8 | (data.get(offset + 2) & 0xFF);
		}
		int value = Math.min(255, (data.get(offset) & 0xFF) * 255);
		return value << 16 | value << 8 | value;
	}

	private static void writeNpy(Path file, StreamSpec spec, ByteBuffer data) throws IOException {
		String header = String.format(Locale.ROOT, "{'descr': '<%s', 'fortran_order': False, 'shape': (%d, %d, %d), }",
			spec.dtype().numpy, spec.height(), spec.width(), spec.channels());
		int unpadded = 10 + header.length() + 1;
		String padded = header + " ".repeat((64 - unpadded % 64) % 64) + "\n";
		try (OutputStream out = Files.newOutputStream(file)) {
			out.write(new byte[]{(byte) 0x93, 'N', 'U', 'M', 'P', 'Y', 1, 0});
			int length = padded.length();
			out.write(new byte[]{(byte) (length & 0xFF), (byte) (length >> 8)});
			out.write(padded.getBytes(StandardCharsets.US_ASCII));
			out.write(rawBytes(data));
		}
	}

	/** GPU readbacks are in native order, which is little-endian on every supported platform. */
	private static byte[] rawBytes(ByteBuffer data) {
		byte[] bytes = new byte[data.remaining()];
		data.duplicate().get(bytes);
		return bytes;
	}
}
