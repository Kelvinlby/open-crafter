package mod.kelvinlby.opencrafter.encoder.format;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.zip.CRC32;

/**
 * Writes one self-contained {@code .mcrec} segment (zstd blocks and a trailer), readable by
 * {@link SegmentReader} and by the recorders' own tools. Written to a temporary file and moved
 * into place on {@link #close()}, so a half-written file never looks finished.
 */
public final class SegmentWriter implements Closeable {
	private static final int BLOCK_BYTES = 1 << 20;
	private static final int ZSTD_LEVEL = 6;

	private final Path target;
	private final Path temp;
	private final DataOutputStream out;
	private final ByteArrayOutputStream block = new ByteArrayOutputStream(BLOCK_BYTES + (64 << 10));
	private final CRC32 crc = new CRC32();
	private long blockFirstMicros;
	private long lastMicros;
	private long recordCount;
	private int blockCount;
	private boolean blockEmpty = true;

	public SegmentWriter(Path target, McrecHeader header) throws IOException {
		this.target = target;
		this.temp = target.resolveSibling(target.getFileName() + ".tmp");
		Files.createDirectories(target.toAbsolutePath().getParent());
		this.out = new DataOutputStream(new BufferedOutputStream(Files.newOutputStream(temp), 1 << 16));
		McrecHeader written = header.copy();
		written.json().addProperty("codecId", BlockCodec.ZSTD.id);
		written.json().addProperty("codecName", BlockCodec.ZSTD.wireName);
		written.json().addProperty("blockSize", BLOCK_BYTES);
		written.json().addProperty("segmentIndex", 0);
		written.json().remove("previousSegment");
		byte[] json = written.toBytes();
		out.write(McrecFormat.MAGIC);
		out.writeInt(json.length);
		out.write(json);
	}

	public void write(Rec rec) throws IOException {
		if (blockEmpty) {
			blockFirstMicros = rec.timeMicros();
			lastMicros = rec.timeMicros();
			blockEmpty = false;
		}
		block.write(rec.flags());
		VarInts.writeVarLong(block, Math.max(0, rec.timeMicros() - lastMicros));
		VarInts.writeVarInt(block, rec.data().length);
		block.write(rec.data());
		lastMicros = Math.max(lastMicros, rec.timeMicros());
		recordCount++;
		if (block.size() >= BLOCK_BYTES) {
			flushBlock();
		}
	}

	private void flushBlock() throws IOException {
		if (blockEmpty) {
			return;
		}
		byte[] raw = block.toByteArray();
		byte[] compressed = BlockCodec.ZSTD.compress(raw, raw.length, ZSTD_LEVEL);
		crc.reset();
		crc.update(raw);
		out.write(McrecFormat.BLOCK_MARKER);
		out.writeInt(raw.length);
		out.writeInt(compressed.length);
		out.writeInt((int) crc.getValue());
		out.writeLong(blockFirstMicros);
		out.write(compressed);
		block.reset();
		blockEmpty = true;
		blockCount++;
	}

	@Override
	public void close() throws IOException {
		flushBlock();
		out.write(McrecFormat.TRAILER_MARKER);
		out.writeInt(blockCount);
		out.writeLong(recordCount);
		out.writeLong(lastMicros);
		out.writeInt(0);
		out.write(McrecFormat.TRAILER_TEXT);
		out.close();
		Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}
}
