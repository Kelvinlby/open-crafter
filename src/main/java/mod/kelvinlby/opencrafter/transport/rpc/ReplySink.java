package mod.kelvinlby.opencrafter.transport.rpc;

/** Accepts an encoded, framed reply for one connection. Must not block. */
@FunctionalInterface
public interface ReplySink {
	void send(byte[] frame);
}
