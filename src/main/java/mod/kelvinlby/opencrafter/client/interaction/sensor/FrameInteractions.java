package mod.kelvinlby.opencrafter.client.interaction.sensor;

import mod.kelvinlby.opencrafter.api.frame.FrameAnnotator;
import mod.kelvinlby.opencrafter.client.interaction.capture.FrameClock;
import mod.kelvinlby.opencrafter.client.interaction.event.InteractionEvent;
import mod.kelvinlby.opencrafter.client.interaction.event.InteractionListener;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Frame meta {@code interactions}: the actions sent since the previous captured frame, with their
 * contact point projected into this frame. Also tells the {@link FrameClock} which frame was captured.
 */
public final class FrameInteractions implements FrameAnnotator, InteractionListener {
	/** Kept while no frames are captured (no backend reading), so the list cannot grow without bound. */
	private static final int MAX_BUFFERED = 256;

	private final FrameClock clock;
	private final List<InteractionEvent> sinceLastFrame = new ArrayList<>();

	public FrameInteractions(FrameClock clock) {
		this.clock = clock;
	}

	@Override
	public String key() {
		return "interactions";
	}

	@Override
	public void onInteraction(InteractionEvent event) {
		if (sinceLastFrame.size() >= MAX_BUFFERED) {
			sinceLastFrame.removeFirst();
		}
		sinceLastFrame.add(event);
	}

	@Override
	public @Nullable Object annotate(long frameIndex, int width, int height) {
		clock.onCaptured(frameIndex);
		List<Map<String, Object>> entries = sinceLastFrame.stream().map(event -> entry(event, width, height)).toList();
		sinceLastFrame.clear();
		return entries;
	}

	private static Map<String, Object> entry(InteractionEvent event, int width, int height) {
		Map<String, Object> map = new HashMap<>();
		map.put("id", event.id());
		map.put("kind", event.kind().wireName());
		map.put("contact_px", event.contact() == null ? null : ScreenProjection.pixels(event.contact(), width, height));
		return map;
	}
}
