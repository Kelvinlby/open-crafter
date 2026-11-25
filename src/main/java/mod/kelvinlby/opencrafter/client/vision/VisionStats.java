package mod.kelvinlby.opencrafter.client.vision;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/** Counters written by the render thread and read by {@code vision.status} on any thread. */
public final class VisionStats {
	private final AtomicLong captured = new AtomicLong();
	private final AtomicLong published = new AtomicLong();
	private final AtomicLong droppedNoReadback = new AtomicLong();
	private final AtomicLong droppedNoSlot = new AtomicLong();
	private final AtomicLong lastFrameIndex = new AtomicLong(-1);
	private volatile int width;
	private volatile int height;
	private volatile String backend = "unknown";

	public void onCaptured(int width, int height) {
		captured.incrementAndGet();
		this.width = width;
		this.height = height;
	}

	public void onPublished(long frameIndex) {
		published.incrementAndGet();
		lastFrameIndex.set(frameIndex);
	}

	public void onDroppedNoReadback() {
		droppedNoReadback.incrementAndGet();
	}

	public void onDroppedNoSlot() {
		droppedNoSlot.incrementAndGet();
	}

	public void setBackend(String backend) {
		this.backend = backend;
	}

	public Map<String, Object> toMap() {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("frames_captured", captured.get());
		map.put("frames_published", published.get());
		map.put("frames_dropped_busy_gpu", droppedNoReadback.get());
		map.put("frames_dropped_no_slot", droppedNoSlot.get());
		map.put("last_frame_index", lastFrameIndex.get());
		map.put("width", width);
		map.put("height", height);
		map.put("backend", backend);
		return map;
	}
}
