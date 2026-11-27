package mod.kelvinlby.opencrafter.api.frame;

import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FrameAnnotatorsTest {
	@Test
	void keepsRegistrationOrderAndLeavesOutNullValues() {
		FrameAnnotators annotators = new FrameAnnotators();
		annotators.register(annotator("b", 2));
		annotators.register(annotator("skipped", null));
		annotators.register(annotator("a", 1));
		Map<String, Object> entries = annotators.annotate(7L, 854, 480);
		assertEquals(List.of("b", "a"), List.copyOf(entries.keySet()));
	}

	@Test
	void refusesADuplicateKey() {
		FrameAnnotators annotators = new FrameAnnotators();
		annotators.register(annotator("x", 1));
		assertThrows(IllegalStateException.class, () -> annotators.register(annotator("x", 2)));
	}

	private static FrameAnnotator annotator(String key, @Nullable Object value) {
		return new FrameAnnotator() {
			@Override
			public String key() {
				return key;
			}

			@Override
			public @Nullable Object annotate(long frameIndex, int width, int height) {
				return value;
			}
		};
	}
}
