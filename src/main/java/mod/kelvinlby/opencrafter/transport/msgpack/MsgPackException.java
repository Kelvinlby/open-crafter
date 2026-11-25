package mod.kelvinlby.opencrafter.transport.msgpack;

/** Malformed MessagePack input, or a value that cannot be encoded. */
public final class MsgPackException extends RuntimeException {
	public MsgPackException(String message) {
		super(message);
	}
}
