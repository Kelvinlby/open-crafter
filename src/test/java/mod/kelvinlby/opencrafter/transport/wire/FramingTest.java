package mod.kelvinlby.opencrafter.transport.wire;

import mod.kelvinlby.opencrafter.transport.msgpack.MsgPackReader;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.net.ProtocolException;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class FramingTest {
	/** Delivers at most {@code chunk} bytes per read, to exercise partial reads. */
	private static ReadableByteChannel channel(byte[] data, int chunk) {
		return new ReadableByteChannel() {
			private int position;

			@Override
			public int read(ByteBuffer target) {
				if (position == data.length) {
					return -1;
				}
				int count = Math.min(Math.min(chunk, target.remaining()), data.length - position);
				target.put(data, position, count);
				position += count;
				return count;
			}

			@Override
			public boolean isOpen() {
				return true;
			}

			@Override
			public void close() {
			}
		};
	}

	private static byte[] concat(byte[]... parts) {
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		for (byte[] part : parts) {
			out.writeBytes(part);
		}
		return out.toByteArray();
	}

	private static Object body(MessageInput input, int length) {
		return MsgPackReader.decode(input.array(), input.offset(), length);
	}

	@Test
	void frameHasLittleEndianLengthPrefix() {
		byte[] frame = WireFormat.message(Map.of("op", "x"));
		int length = frame[0] & 0xff | (frame[1] & 0xff) << 8 | (frame[2] & 0xff) << 16 | (frame[3] & 0xff) << 24;
		assertEquals(frame.length - 4, length);
		assertEquals(Map.of("op", "x"), MsgPackReader.decode(frame, 4, length));
	}

	@Test
	void envelopesHaveDocumentedShape() {
		assertEquals(Map.of("id", 7L, "ok", true, "result", List.of(1L)), decodeFrame(WireFormat.success(7L, List.of(1))));
		assertEquals(Map.of("id", 7L, "ok", false, "error", "boom"), decodeFrame(WireFormat.failure(7L, "boom")));
		assertEquals(Map.of("event", "e", "data", Map.of("a", 1L)), decodeFrame(WireFormat.event("e", Map.of("a", 1))));
	}

	private static Object decodeFrame(byte[] frame) {
		return MsgPackReader.decode(frame, 4, frame.length - 4);
	}

	@Test
	void splitsConcatenatedMessagesDeliveredByteByByte() throws IOException {
		byte[] big = new byte[200_000];
		Arrays.fill(big, (byte) 3);
		byte[] stream = concat(WireFormat.message("a"), WireFormat.message(big), WireFormat.message(Map.of()));
		for (int chunk : new int[] {1, 7, 4096, Integer.MAX_VALUE}) {
			MessageInput input = new MessageInput(channel(stream, chunk));
			assertEquals("a", body(input, input.next()));
			assertArrayEquals(big, (byte[]) body(input, input.next()));
			assertEquals(Map.of(), body(input, input.next()));
			assertEquals(-1, input.next());
		}
	}

	@Test
	void eofInsideMessageIsAnError() {
		byte[] frame = WireFormat.message("hello");
		MessageInput input = new MessageInput(channel(Arrays.copyOf(frame, frame.length - 1), 3));
		assertThrows(EOFException.class, input::next);
	}

	@Test
	void eofInsideLengthPrefixIsAnError() {
		MessageInput input = new MessageInput(channel(new byte[] {1, 0}, 1));
		assertThrows(EOFException.class, input::next);
	}

	@Test
	void oversizedLengthIsRejected() {
		byte[] prefix = {(byte) 0xff, (byte) 0xff, (byte) 0xff, (byte) 0x7f};
		MessageInput input = new MessageInput(channel(prefix, 4));
		assertThrows(ProtocolException.class, input::next);
	}

	@Test
	void emptyBodyIsAllowedAtFramingLevel() throws IOException {
		MessageInput input = new MessageInput(channel(new byte[] {0, 0, 0, 0}, 4));
		assertEquals(0, input.next());
		assertEquals(-1, input.next());
	}
}
