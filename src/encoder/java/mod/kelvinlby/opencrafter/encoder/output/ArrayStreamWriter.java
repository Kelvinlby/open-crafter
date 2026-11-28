package mod.kelvinlby.opencrafter.encoder.output;

import com.github.luben.zstd.Zstd;
import com.google.gson.JsonObject;

import java.io.BufferedOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;

/**
 * A per-frame array stream ({@code .ocs}): every frame is encoded on its own (so a reader can seek
 * to any frame) and the file ends with an offset index.
 *
 * <pre>
 * "OCSTREAM1\n"  u32 headerLength  header JSON
 * frames:        u32 length  payload           (one per frame)
 * index:         u64 offset × frames  u32 frames  u64 indexOffset  "OCSINDEX"
 * </pre>
 * All integers big-endian. The header names the frame shape and the {@link ArrayCodec} used.
 */
public final class ArrayStreamWriter implements AutoCloseable {
	static final byte[] MAGIC = "OCSTREAM1\n".getBytes(StandardCharsets.US_ASCII);
	static final byte[] INDEX_MAGIC = "OCSINDEX".getBytes(StandardCharsets.US_ASCII);

	private final Path target;
	private final Path temp;
	private final DataOutputStream out;
	private final ArrayCodec codec;
	private final int zstdLevel;
	private final int width;
	private final int height;
	private long position;
	private long[] offsets = new long[1024];
	private int frames;
	private long rawBytes;

	public ArrayStreamWriter(Path target, String name, int width, int height, ArrayCodec codec, int zstdLevel) throws IOException {
		this.target = target;
		this.temp = target.resolveSibling(target.getFileName() + ".tmp");
		this.codec = codec;
		this.zstdLevel = zstdLevel;
		this.width = width;
		this.height = height;
		this.out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temp), 1 << 20));
		byte[] header = header(name, width, height, codec).toString().getBytes(StandardCharsets.UTF_8);
		out.write(MAGIC);
		out.writeInt(header.length);
		out.write(header);
		position = MAGIC.length + 4L + header.length;
	}

	/** One frame packed and compressed, ready to append; {@code rawBytes} is its size as delivered. */
	public record Encoded(byte[] compressed, long rawBytes) {
	}

	/** One frame as the vision sensor delivers it (little-endian elements, top row first). */
	public void write(ByteBuffer frame) throws IOException {
		append(encode(frame));
	}

	/** Packs and compresses one frame. Touches no writer state, so frames can be encoded on any thread. */
	public Encoded encode(ByteBuffer frame) {
		ByteBuffer source = frame.duplicate();
		long raw = source.remaining();
		return new Encoded(Zstd.compress(codec.pack(source, width, height), zstdLevel), raw);
	}

	/** Appends an encoded frame; frames must be appended in frame order. */
	public void append(Encoded frame) throws IOException {
		rawBytes += frame.rawBytes();
		if (frames == offsets.length) {
			offsets = Arrays.copyOf(offsets, frames * 2);
		}
		offsets[frames++] = position;
		out.writeInt(frame.compressed().length);
		out.write(frame.compressed());
		position += 4L + frame.compressed().length;
	}

	public int frames() {
		return frames;
	}

	public long rawBytes() {
		return rawBytes;
	}

	public long storedBytes() {
		return position;
	}

	@Override
	public void close() throws IOException {
		long indexOffset = position;
		for (int i = 0; i < frames; i++) {
			out.writeLong(offsets[i]);
		}
		out.writeInt(frames);
		out.writeLong(indexOffset);
		out.write(INDEX_MAGIC);
		out.close();
		Files.move(temp, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING, java.nio.file.StandardCopyOption.ATOMIC_MOVE);
	}

	private static JsonObject header(String name, int width, int height, ArrayCodec codec) {
		JsonObject header = new JsonObject();
		header.addProperty("name", name);
		header.addProperty("width", width);
		header.addProperty("height", height);
		header.addProperty("channels", codec.channels());
		header.addProperty("encoding", codec.encoding());
		header.addProperty("dtype", codec.decodedDtype());
		header.addProperty("compression", "zstd");
		if (codec.scale() != 1.0) {
			header.addProperty("scale", codec.scale());
		}
		return header;
	}
}
