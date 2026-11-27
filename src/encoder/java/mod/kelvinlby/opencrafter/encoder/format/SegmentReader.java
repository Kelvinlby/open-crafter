package mod.kelvinlby.opencrafter.encoder.format;

import org.jspecify.annotations.Nullable;

import java.io.BufferedInputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.zip.CRC32;

/**
 * Streams the records of one segment. Forgiving at the tail: a torn or corrupt block ends the
 * stream (recorded in {@link #truncation()}) instead of throwing, so a crashed recording still
 * yields every block that reached the disk.
 */
public final class SegmentReader implements Closeable {
	private final String name;
	private final InputStream in;
	private final McrecHeader header;
	private final BlockCodec codec;
	private final CRC32 crc = new CRC32();

	private byte @Nullable [] block;
	private int pos;
	private long time;
	private boolean firstOfBlock;
	private boolean exhausted;
	private @Nullable String truncation;
	private @Nullable String nextSegment;

	private SegmentReader(String name, InputStream in, McrecHeader header, BlockCodec codec) {
		this.name = name;
		this.in = in;
		this.header = header;
		this.codec = codec;
	}

	/** Takes ownership of {@code source}: it is closed with the reader, or on a failed open. */
	public static SegmentReader open(String name, InputStream source) throws IOException {
		InputStream in = new BufferedInputStream(source, 1 << 16);
		try {
			byte[] magic = in.readNBytes(McrecFormat.MAGIC.length);
			if (!Arrays.equals(magic, McrecFormat.MAGIC)) {
				throw new IOException("Not a recording (bad magic): " + name);
			}
			int headerLength = readInt(in.readNBytes(4), 0);
			if (headerLength < 0 || headerLength > 64 << 20) {
				throw new IOException("Implausible header length " + headerLength + " in " + name);
			}
			McrecHeader header = McrecHeader.parse(in.readNBytes(headerLength));
			if (header.formatVersion() > McrecFormat.FORMAT_VERSION) {
				throw new IOException("Recording format " + header.formatVersion() + " is newer than this reader: " + name);
			}
			return new SegmentReader(name, in, header, BlockCodec.byId(header.codecId()));
		} catch (IOException | RuntimeException e) {
			in.close();
			throw e;
		}
	}

	public McrecHeader header() {
		return header;
	}

	public String name() {
		return name;
	}

	/** Why the segment ended early, or null if it ended with a valid trailer (or is not finished). */
	public @Nullable String truncation() {
		return truncation;
	}

	/** File name of the next segment, known once a clean trailer was read. */
	public @Nullable String nextSegment() {
		return nextSegment;
	}

	/** The next record, or null at the end of the segment. */
	public @Nullable Rec next() throws IOException {
		while (block == null || pos >= block.length) {
			if (exhausted || !readBlock()) {
				return null;
			}
		}
		int flags = block[pos++] & 0xFF;
		long delta = readVarLong();
		int length = (int) readVarLong();
		if (length < 0 || pos + length > block.length) {
			throw new IOException("Record runs past its block in " + name);
		}
		byte[] data = Arrays.copyOfRange(block, pos, pos + length);
		pos += length;
		time = firstOfBlock ? time : time + delta;
		firstOfBlock = false;
		return new Rec(flags, time, data);
	}

	private boolean readBlock() throws IOException {
		int marker = in.read();
		if (marker == McrecFormat.TRAILER_MARKER) {
			readTrailer();
			return end(null);
		}
		if (marker != McrecFormat.BLOCK_MARKER) {
			return end(marker < 0 ? "no trailer (recording was not closed cleanly)" : "unexpected marker 0x" + Integer.toHexString(marker));
		}
		byte[] head = in.readNBytes(McrecFormat.BLOCK_HEADER_BYTES);
		if (head.length != McrecFormat.BLOCK_HEADER_BYTES) {
			return end("torn block header");
		}
		int rawLength = readInt(head, 0);
		int compressedLength = readInt(head, 4);
		if (rawLength < 0 || compressedLength < 0 || rawLength > McrecFormat.MAX_BLOCK_BYTES || compressedLength > McrecFormat.MAX_BLOCK_BYTES) {
			return end("implausible block lengths");
		}
		byte[] compressed = in.readNBytes(compressedLength);
		if (compressed.length != compressedLength) {
			return end("torn block payload");
		}
		byte[] raw;
		try {
			raw = codec.decompress(compressed, rawLength);
		} catch (IOException e) {
			return end("corrupt block: " + e.getMessage());
		}
		crc.reset();
		crc.update(raw);
		if ((int) crc.getValue() != readInt(head, 8)) {
			return end("block checksum mismatch");
		}
		block = raw;
		pos = 0;
		time = readLong(head, 12);
		firstOfBlock = true;
		return true;
	}

	private void readTrailer() throws IOException {
		byte[] fixed = in.readNBytes(24);
		if (fixed.length != 24) {
			truncation = "torn trailer";
			return;
		}
		int nextLength = readInt(fixed, 20);
		byte[] next = in.readNBytes(Math.clamp(nextLength, 0, 4096));
		byte[] text = in.readNBytes(McrecFormat.TRAILER_TEXT.length);
		if (next.length != nextLength || !Arrays.equals(text, McrecFormat.TRAILER_TEXT)) {
			truncation = "corrupt trailer";
			return;
		}
		nextSegment = nextLength == 0 ? null : new String(next, StandardCharsets.UTF_8);
	}

	private boolean end(@Nullable String reason) {
		exhausted = true;
		if (reason != null) {
			truncation = reason;
		}
		return false;
	}

	private long readVarLong() throws IOException {
		long result = 0;
		for (int shift = 0; shift < 70; shift += 7) {
			if (pos >= block.length) {
				throw new IOException("Record header runs past its block in " + name);
			}
			int b = block[pos++] & 0xFF;
			result |= (long) (b & 0x7F) << shift;
			if ((b & 0x80) == 0) {
				return result;
			}
		}
		throw new IOException("varlong too long in " + name);
	}

	private static int readInt(byte[] b, int off) throws IOException {
		if (b.length < off + 4) {
			throw new IOException("Unexpected end of file");
		}
		return (b[off] & 0xFF) << 24 | (b[off + 1] & 0xFF) << 16 | (b[off + 2] & 0xFF) << 8 | (b[off + 3] & 0xFF);
	}

	private static long readLong(byte[] b, int off) {
		long value = 0;
		for (int i = 0; i < 8; i++) {
			value = value << 8 | (b[off + i] & 0xFF);
		}
		return value;
	}

	@Override
	public void close() throws IOException {
		in.close();
	}
}
