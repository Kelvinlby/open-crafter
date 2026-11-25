package mod.kelvinlby.opencrafter.api.frame;

import java.util.List;

/** Writes frames into shared memory for the backend. Never blocks. */
public interface FramePublisher {
	/** True when at least one backend is connected, so capture work is worth doing. */
	boolean hasReaders();

	/**
	 * Acquires a slot laid out for the given streams, or {@code null} if none is
	 * available right now (the frame should then be dropped, not waited for).
	 */
	FrameSlot acquire(List<StreamSpec> streams);
}
