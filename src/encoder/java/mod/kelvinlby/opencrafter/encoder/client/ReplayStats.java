package mod.kelvinlby.opencrafter.encoder.client;

import java.util.LinkedHashMap;
import java.util.Map;

/** Counters of one replay, for the episode summary and the progress log. */
final class ReplayStats {
	private final long startedNanos = System.nanoTime();
	private long playingStartedNanos;
	private long loadedMicros;
	private long framesCaptured;
	private long settleRenders;
	private long recordedTicks;

	void onPlayingStarted(long recordingMicros) {
		playingStartedNanos = System.nanoTime();
		loadedMicros = recordingMicros;
	}

	void onFrameCaptured() {
		framesCaptured++;
	}

	void onSettleRender() {
		settleRenders++;
	}

	void onRecordedTicks(int ticks) {
		recordedTicks += ticks;
	}

	long framesCaptured() {
		return framesCaptured;
	}

	Map<String, Object> toMap() {
		Map<String, Object> map = new LinkedHashMap<>();
		double playingSeconds = playingStartedNanos == 0 ? 0 : (System.nanoTime() - playingStartedNanos) / 1e9;
		map.put("frames", framesCaptured);
		map.put("recorded_ticks_replayed", recordedTicks);
		map.put("chunk_settle_renders", settleRenders);
		map.put("world_loaded_at_recording_seconds", loadedMicros / 1e6);
		map.put("wall_seconds_total", (System.nanoTime() - startedNanos) / 1e9);
		map.put("wall_seconds_playing", playingSeconds);
		map.put("frames_per_wall_second", playingSeconds > 0 ? framesCaptured / playingSeconds : 0);
		return map;
	}
}
