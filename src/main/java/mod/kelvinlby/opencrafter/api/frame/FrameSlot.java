package mod.kelvinlby.opencrafter.api.frame;

import java.nio.ByteBuffer;
import java.util.Map;

/** A writable frame in the shared-memory ring. Exactly one of commit/abort must be called. */
public interface FrameSlot {
	/** Writable buffer (position 0, limit = spec byte size) for the named stream. */
	ByteBuffer stream(String name);

	/** Small per-frame metadata (camera, tick, status snapshot ...), MessagePack-encodable. */
	void setMeta(Map<String, Object> meta);

	/** Publishes the frame atomically to readers. */
	void commit(long frameIndex, long captureNanos);

	void abort();
}
