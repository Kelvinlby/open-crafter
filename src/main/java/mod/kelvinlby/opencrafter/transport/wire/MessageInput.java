package mod.kelvinlby.opencrafter.transport.wire;

import java.io.EOFException;
import java.io.IOException;
import java.net.ProtocolException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;

/**
 * Splits a byte stream into length-prefixed messages. Reads as much as the channel offers
 * per syscall, so a burst of small requests costs one read. Not thread-safe.
 */
public final class MessageInput {
	private static final int INITIAL_CAPACITY = 64 * 1024;

	private final ReadableByteChannel channel;
	private byte[] data = new byte[INITIAL_CAPACITY];
	private ByteBuffer window = ByteBuffer.wrap(data);
	private int start;
	private int end;
	private int consumed;

	public MessageInput(ReadableByteChannel channel) {
		this.channel = channel;
	}

	/**
	 * Blocks until a complete message is buffered and returns its body length, or -1 on a
	 * clean end of stream. The body is {@code array()[offset() .. offset() + length)} and
	 * stays valid until the next call.
	 */
	public int next() throws IOException {
		discardConsumed();
		if (!fill(WireFormat.LENGTH_PREFIX_BYTES, true)) {
			return -1;
		}
		int length = bodyLength();
		fill(WireFormat.LENGTH_PREFIX_BYTES + length, false);
		consumed = WireFormat.LENGTH_PREFIX_BYTES + length;
		return length;
	}

	public byte[] array() {
		return data;
	}

	public int offset() {
		return start + WireFormat.LENGTH_PREFIX_BYTES;
	}

	private void discardConsumed() {
		start += consumed;
		consumed = 0;
		if (start == end) {
			start = 0;
			end = 0;
		}
	}

	private int bodyLength() throws ProtocolException {
		int length = (data[start] & 0xff)
			| (data[start + 1] & 0xff) << 8
			| (data[start + 2] & 0xff) << 16
			| (data[start + 3] & 0xff) << 24;
		if (length < 0 || length > WireFormat.MAX_MESSAGE_BYTES) {
			throw new ProtocolException("Message length out of range: " + Integer.toUnsignedString(length));
		}
		return length;
	}

	/** Ensures {@code count} bytes are buffered from {@code start}; false only on EOF at a boundary. */
	private boolean fill(int count, boolean eofAllowed) throws IOException {
		if (end - start >= count) {
			return true;
		}
		makeRoom(count);
		while (end - start < count) {
			window.limit(data.length).position(end);
			int read = channel.read(window);
			if (read < 0) {
				if (eofAllowed && end == start) {
					return false;
				}
				throw new EOFException("Connection closed mid-message");
			}
			end += read;
		}
		return true;
	}

	private void makeRoom(int count) {
		if (data.length - start >= count) {
			return;
		}
		byte[] target = count > data.length ? new byte[Math.max(count, data.length * 2)] : data;
		System.arraycopy(data, start, target, 0, end - start);
		end -= start;
		start = 0;
		if (target != data) {
			data = target;
			window = ByteBuffer.wrap(data);
		}
	}
}
