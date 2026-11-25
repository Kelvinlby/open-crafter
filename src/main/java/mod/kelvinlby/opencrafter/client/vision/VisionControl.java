package mod.kelvinlby.opencrafter.client.vision;

import mod.kelvinlby.opencrafter.api.frame.FramePublisher;
import mod.kelvinlby.opencrafter.client.vision.compat.ShaderModCompat;
import mod.kelvinlby.opencrafter.core.config.VisionConfig.RateMode;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The user-facing control surface of the vision sensor, shared by the backend ops and the
 * {@code /opencrafter vision} client command. Thread-safe.
 */
public final class VisionControl {
	private final VisionSettings settings;
	private final VisionStats stats;
	private final FramePublisher publisher;

	public VisionControl(VisionSettings settings, VisionStats stats, FramePublisher publisher) {
		this.settings = settings;
		this.stats = stats;
		this.publisher = publisher;
	}

	public void setStreams(Collection<VisionStream> streams) {
		settings.setStreams(streams);
	}

	/** Persists the rate; performs file I/O, so call it off the render thread. */
	public void setRate(RateMode mode, double fixedHz) {
		settings.setRate(mode, fixedHz);
	}

	public VisionSettings.Snapshot settings() {
		return settings.get();
	}

	public Map<String, Object> status() {
		VisionSettings.Snapshot snapshot = settings.get();
		Map<String, Object> status = new LinkedHashMap<>();
		status.put("streams", streamNames(snapshot.streams()));
		status.put("rate_mode", RateModes.wireName(snapshot.rateMode()));
		status.put("fixed_hz", snapshot.fixedHz());
		status.put("has_readers", publisher.hasReaders());
		status.put("shader_pack_in_use", ShaderModCompat.isShaderPackInUse());
		status.put("iris_loaded", ShaderModCompat.isIrisLoaded());
		status.putAll(stats.toMap());
		return status;
	}

	private static List<String> streamNames(Collection<VisionStream> streams) {
		List<String> names = new ArrayList<>();
		for (VisionStream stream : VisionStream.values()) {
			if (streams.contains(stream)) {
				names.add(stream.wireName);
			}
		}
		return names;
	}
}
