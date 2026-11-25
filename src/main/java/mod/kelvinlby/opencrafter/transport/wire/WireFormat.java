package mod.kelvinlby.opencrafter.transport.wire;

import mod.kelvinlby.opencrafter.transport.msgpack.MsgPackWriter;

import java.util.function.Consumer;

/**
 * Encodes control-channel messages as ready-to-send frames:
 * {@code u32 little-endian body length} followed by one MessagePack map.
 * Envelopes are written field by field, so no intermediate maps are allocated.
 */
public final class WireFormat {
	public static final int PROTOCOL_VERSION = 1;
	public static final int LENGTH_PREFIX_BYTES = 4;
	public static final int MAX_MESSAGE_BYTES = 64 << 20;

	private static final int RETAINED_WRITER_BYTES = 1 << 20;
	private static final ThreadLocal<MsgPackWriter> WRITERS = ThreadLocal.withInitial(WireFormat::newWriter);

	private WireFormat() {
	}

	/** {@code {"id": id, "ok": true, "result": result}}. */
	public static byte[] success(Object id, Object result) {
		return frame(writer -> {
			writer.writeMapHeader(3);
			writer.writeString("id").writeValue(id);
			writer.writeString("ok").writeBool(true);
			writer.writeString("result").writeValue(result);
		});
	}

	/** {@code {"id": id, "ok": false, "error": error}}. */
	public static byte[] failure(Object id, String error) {
		return frame(writer -> {
			writer.writeMapHeader(3);
			writer.writeString("id").writeValue(id);
			writer.writeString("ok").writeBool(false);
			writer.writeString("error").writeString(error);
		});
	}

	/** {@code {"event": name, "data": data}}. */
	public static byte[] event(String name, Object data) {
		return frame(writer -> {
			writer.writeMapHeader(2);
			writer.writeString("event").writeString(name);
			writer.writeString("data").writeValue(data);
		});
	}

	/** Frames any single value (used for requests by clients and tests). */
	public static byte[] message(Object value) {
		return frame(writer -> writer.writeValue(value));
	}

	private static byte[] frame(Consumer<MsgPackWriter> body) {
		MsgPackWriter writer = WRITERS.get();
		writer.reset();
		try {
			int prefix = writer.reserve(LENGTH_PREFIX_BYTES);
			body.accept(writer);
			int length = writer.size() - LENGTH_PREFIX_BYTES;
			requireWithinLimit(length);
			writer.patchIntLE(prefix, length);
			return writer.toByteArray();
		} finally {
			releaseIfOversized(writer);
		}
	}

	private static void requireWithinLimit(int length) {
		if (length > MAX_MESSAGE_BYTES) {
			throw new IllegalArgumentException("Message of " + length + " bytes exceeds " + MAX_MESSAGE_BYTES);
		}
	}

	private static void releaseIfOversized(MsgPackWriter writer) {
		if (writer.capacity() > RETAINED_WRITER_BYTES) {
			WRITERS.set(newWriter());
		}
	}

	private static MsgPackWriter newWriter() {
		return new MsgPackWriter(4096);
	}
}
