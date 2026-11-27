package mod.kelvinlby.opencrafter.api.frame;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/** Every registered {@link FrameAnnotator}; the vision sensor asks them all for each captured frame. */
public final class FrameAnnotators {
	private final List<FrameAnnotator> annotators = new CopyOnWriteArrayList<>();

	public void register(FrameAnnotator annotator) {
		if (annotators.stream().anyMatch(existing -> existing.key().equals(annotator.key()))) {
			throw new IllegalStateException("Duplicate frame annotation: " + annotator.key());
		}
		annotators.add(annotator);
	}

	/** The non-null annotations for this frame, in registration order. */
	public Map<String, Object> annotate(long frameIndex, int width, int height) {
		Map<String, Object> entries = new LinkedHashMap<>();
		for (FrameAnnotator annotator : annotators) {
			Object value = annotator.annotate(frameIndex, width, height);
			if (value != null) {
				entries.put(annotator.key(), value);
			}
		}
		return entries;
	}
}
