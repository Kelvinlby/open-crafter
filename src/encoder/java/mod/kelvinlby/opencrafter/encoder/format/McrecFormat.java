package mod.kelvinlby.opencrafter.encoder.format;

import java.nio.charset.StandardCharsets;

/**
 * Constants of the recorder's {@code .mcrec} container (format 1). Both recorder projects
 * (1.21.11 and 26.3) write it byte for byte the same; see their READMEs for the full spec.
 */
public final class McrecFormat {
	public static final byte[] MAGIC = "MCREC1\n".getBytes(StandardCharsets.US_ASCII);
	public static final byte[] TRAILER_TEXT = "MCRECEND".getBytes(StandardCharsets.US_ASCII);
	public static final int FORMAT_VERSION = 1;

	public static final int BLOCK_MARKER = 0xB1;
	public static final int TRAILER_MARKER = 0xEE;
	/** Block header after the marker: raw length, compressed length, CRC32, first record time. */
	public static final int BLOCK_HEADER_BYTES = 20;
	public static final int MAX_BLOCK_BYTES = 256 * 1024 * 1024;

	public static final int FLAG_C2S = 0x01;
	public static final int PHASE_SHIFT = 1;
	public static final int PHASE_MASK = 0x0E;
	public static final int FLAG_GAP = 0x10;

	public static final int CODEC_NONE = 0;
	public static final int CODEC_DEFLATE = 1;
	public static final int CODEC_ZSTD = 2;

	private McrecFormat() {
	}
}
