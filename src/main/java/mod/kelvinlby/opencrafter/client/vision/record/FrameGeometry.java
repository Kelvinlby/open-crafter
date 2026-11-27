package mod.kelvinlby.opencrafter.client.vision.record;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;

/**
 * All moving geometry recorded for one captured frame, by submit key, plus how hand objects
 * relate to the frames around it (see {@link MotionRecorder#beginHandObject}).
 */
public final class FrameGeometry {
	private final Map<SubmitKey, GeometryRecord> records = new HashMap<>();
	private final Set<Object> owners = new HashSet<>();
	/** Owner in the previous frame → the owner that took over its geometry in this one. */
	private final Map<Object, Object> successors = new HashMap<>();
	/** Owners whose geometry sits exactly on world geometry this frame and leaves it there when it vanishes. */
	private final Set<Object> settled = new HashSet<>();

	public GeometryRecord recordFor(SubmitKey key) {
		owners.add(key.owner());
		return records.computeIfAbsent(key, ignored -> new GeometryRecord(key.phase()));
	}

	public GeometryRecord get(SubmitKey key) {
		return records.get(key);
	}

	public void forEach(BiConsumer<SubmitKey, GeometryRecord> action) {
		records.forEach(action);
	}

	public boolean hasOwner(Object owner) {
		return owners.contains(owner);
	}

	/** {@code successor} carries on what {@code predecessor} drew in the previous frame, if that is gone now. */
	public void noteSuccessor(Object predecessor, Object successor) {
		successors.putIfAbsent(predecessor, successor);
	}

	public Object successorOf(Object predecessor) {
		return successors.get(predecessor);
	}

	/** {@code owner}'s geometry coincides with the world's this frame (a flying block on its target). */
	public void noteSettled(Object owner) {
		settled.add(owner);
	}

	public boolean isSettled(Object owner) {
		return settled.contains(owner);
	}
}
