package mod.kelvinlby.opencrafter.encoder.format;

import com.github.luben.zstd.Zstd;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.zip.DataFormatException;
import java.util.zip.Deflater;
import java.util.zip.Inflater;

/** Block payload codecs of the container: none, zlib deflate, zstd. */
public enum BlockCodec {
	NONE(McrecFormat.CODEC_NONE, "none"),
	DEFLATE(McrecFormat.CODEC_DEFLATE, "deflate"),
	ZSTD(McrecFormat.CODEC_ZSTD, "zstd");

	public final int id;
	public final String wireName;

	BlockCodec(int id, String wireName) {
		this.id = id;
		this.wireName = wireName;
	}

	public static BlockCodec byId(int id) throws IOException {
		for (BlockCodec codec : values()) {
			if (codec.id == id) {
				return codec;
			}
		}
		throw new IOException("Unknown block codec " + id);
	}

	public byte[] decompress(byte[] src, int rawLength) throws IOException {
		return switch (this) {
			case NONE -> src;
			case DEFLATE -> inflate(src, rawLength);
			case ZSTD -> unzstd(src, rawLength);
		};
	}

	public byte[] compress(byte[] src, int length, int level) {
		return switch (this) {
			case NONE -> Arrays.copyOf(src, length);
			case DEFLATE -> deflate(src, length, level);
			case ZSTD -> Zstd.compress(Arrays.copyOf(src, length), level);
		};
	}

	private static byte[] unzstd(byte[] src, int rawLength) throws IOException {
		byte[] out = new byte[rawLength];
		long written = Zstd.decompressByteArray(out, 0, rawLength, src, 0, src.length);
		if (Zstd.isError(written) || written != rawLength) {
			throw new IOException("zstd block corrupt: " + (Zstd.isError(written) ? Zstd.getErrorName(written) : written + " bytes"));
		}
		return out;
	}

	private static byte[] inflate(byte[] src, int rawLength) throws IOException {
		Inflater inflater = new Inflater();
		try {
			inflater.setInput(src);
			byte[] out = new byte[rawLength];
			int n = inflater.inflate(out);
			if (n != rawLength) {
				throw new IOException("deflate block length mismatch: " + n);
			}
			return out;
		} catch (DataFormatException e) {
			throw new IOException("deflate block corrupt", e);
		} finally {
			inflater.end();
		}
	}

	private static byte[] deflate(byte[] src, int length, int level) {
		Deflater deflater = new Deflater(level);
		try {
			deflater.setInput(src, 0, length);
			deflater.finish();
			ByteArrayOutputStream out = new ByteArrayOutputStream(length / 2 + 64);
			byte[] chunk = new byte[1 << 16];
			while (!deflater.finished()) {
				out.write(chunk, 0, deflater.deflate(chunk));
			}
			return out.toByteArray();
		} finally {
			deflater.end();
		}
	}
}
