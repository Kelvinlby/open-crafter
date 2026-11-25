package mod.kelvinlby.opencrafter.client.vision.record;

import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;

/** All moving geometry recorded for one captured frame, by submit key. */
public final class FrameGeometry {
	private final Map<SubmitKey, GeometryRecord> records = new HashMap<>();

	public GeometryRecord recordFor(SubmitKey key) {
		return records.computeIfAbsent(key, ignored -> new GeometryRecord(key.phase()));
	}

	public GeometryRecord get(SubmitKey key) {
		return records.get(key);
	}

	public void forEach(BiConsumer<SubmitKey, GeometryRecord> action) {
		records.forEach(action);
	}

}
