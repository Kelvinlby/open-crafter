package mod.kelvinlby.opencrafter.encoder.client;

import mod.kelvinlby.opencrafter.api.ModContext;
import mod.kelvinlby.opencrafter.api.ModModule;
import mod.kelvinlby.opencrafter.api.frame.FrameAnnotator;
import mod.kelvinlby.opencrafter.api.sensor.Sensor;
import org.jspecify.annotations.Nullable;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The encoder's part of open-crafter (entrypoint {@code open-crafter:modules}): keeps the module
 * context for the replay session and adds two entries to every captured frame's metadata:
 * {@code sensors} (every registered non-visual sensor) and {@code replay} (recording time and the
 * recorded client's input since the previous frame, see {@link ReplayLabels}).
 */
public final class EncoderModule implements ModModule {
	private static volatile @Nullable ModContext context;

	static @Nullable ModContext context() {
		return context;
	}

	@Override
	public void install(ModContext modContext) {
		context = modContext;
		modContext.annotators().register(annotator("sensors", (index, w, h) -> readSensors(modContext)));
		modContext.annotators().register(annotator("replay", (index, w, h) -> ReplaySession.labelsForFrame()));
	}

	private static Map<String, Object> readSensors(ModContext modContext) {
		Map<String, Object> values = new LinkedHashMap<>();
		for (Sensor sensor : modContext.sensors().all()) {
			values.put(sensor.name(), sensor.read());
		}
		return values;
	}

	private interface Annotation {
		@Nullable Object value(long frameIndex, int width, int height);
	}

	private static FrameAnnotator annotator(String key, Annotation annotation) {
		return new FrameAnnotator() {
			@Override
			public String key() {
				return key;
			}

			@Override
			public @Nullable Object annotate(long frameIndex, int width, int height) {
				return annotation.value(frameIndex, width, height);
			}
		};
	}
}
