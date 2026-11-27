package mod.kelvinlby.opencrafter.encoder.format;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.Channels;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.LinkedHashMap;
import java.util.Map;

/** Member offsets of an uncompressed ustar archive (how the 1.21.11 recorder packs a session). */
public final class TarIndex {
	private static final int BLOCK = 512;

	private final Path file;
	private final Map<String, Member> members;

	private TarIndex(Path file, Map<String, Member> members) {
		this.file = file;
		this.members = members;
	}

	public record Member(long offset, long size) {
	}

	public static TarIndex read(Path file) throws IOException {
		Map<String, Member> members = new LinkedHashMap<>();
		try (FileChannel channel = FileChannel.open(file, StandardOpenOption.READ)) {
			ByteBuffer header = ByteBuffer.allocate(BLOCK);
			long position = 0;
			while (position + BLOCK <= channel.size()) {
				header.clear();
				channel.read(header, position);
				byte[] h = header.array();
				if (h[0] == 0) {
					break;
				}
				String name = field(h, 0, 100);
				String prefix = field(h, 345, 155);
				long size = Long.parseLong(field(h, 124, 12).trim(), 8);
				members.put(prefix.isEmpty() ? name : prefix + "/" + name, new Member(position + BLOCK, size));
				position += BLOCK + (size + BLOCK - 1) / BLOCK * BLOCK;
			}
		}
		return new TarIndex(file, members);
	}

	public Map<String, Member> members() {
		return members;
	}

	public InputStream open(String name) throws IOException {
		Member member = members.get(name);
		if (member == null) {
			throw new IOException("No " + name + " in " + file);
		}
		FileChannel channel = FileChannel.open(file, StandardOpenOption.READ);
		channel.position(member.offset());
		return new BoundedInputStream(Channels.newInputStream(channel), member.size());
	}

	private static String field(byte[] header, int offset, int length) {
		int end = offset;
		while (end < offset + length && header[end] != 0) {
			end++;
		}
		return new String(header, offset, end - offset, StandardCharsets.US_ASCII);
	}

	private static final class BoundedInputStream extends InputStream {
		private final InputStream in;
		private long remaining;

		BoundedInputStream(InputStream in, long size) {
			this.in = in;
			this.remaining = size;
		}

		@Override
		public int read() throws IOException {
			if (remaining <= 0) {
				return -1;
			}
			int b = in.read();
			if (b >= 0) {
				remaining--;
			}
			return b;
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			if (remaining <= 0) {
				return -1;
			}
			int n = in.read(b, off, (int) Math.min(len, remaining));
			if (n > 0) {
				remaining -= n;
			}
			return n;
		}

		@Override
		public void close() throws IOException {
			in.close();
		}
	}
}
