package mod.kelvinlby.opencrafter.client.vision;

import mod.kelvinlby.opencrafter.core.config.ConfigStore;
import mod.kelvinlby.opencrafter.core.config.VisionConfig;

import java.util.Collection;
import java.util.EnumSet;
import java.util.Set;

/**
 * Runtime vision settings shared between the transport thread (ops), the main thread
 * (commands) and the render thread (capture). Reads are lock-free snapshots; rate
 * changes are also persisted through the {@link ConfigStore}.
 */
public final class VisionSettings {
	/** An immutable view of the settings at one point in time. */
	public record Snapshot(Set<VisionStream> streams, VisionConfig.RateMode rateMode, double fixedHz) {
		public Snapshot {
			streams = streams.isEmpty() ? Set.of() : Set.copyOf(EnumSet.copyOf(streams));
		}

		public boolean wants(VisionStream stream) {
			return streams.contains(stream);
		}

		public boolean wantsFlow() {
			return wants(VisionStream.FLOW) || wants(VisionStream.FLOW_OCCLUDED);
		}
	}

	public static final double MIN_HZ = 0.1;
	public static final double MAX_HZ = 1000.0;

	private final ConfigStore config;
	private volatile Snapshot current;

	public VisionSettings(ConfigStore config) {
		this.config = config;
		VisionConfig vision = config.get().vision;
		this.current = new Snapshot(EnumSet.of(VisionStream.RGB), vision.rateMode, clampHz(vision.fixedHz));
	}

	public Snapshot get() {
		return current;
	}

	public synchronized void setStreams(Collection<VisionStream> streams) {
		Snapshot old = current;
		current = new Snapshot(streams.isEmpty() ? EnumSet.noneOf(VisionStream.class) : EnumSet.copyOf(streams), old.rateMode(), old.fixedHz());
	}

	/** Changes the push rate and persists it. Performs file I/O: never call on the render thread. */
	public synchronized void setRate(VisionConfig.RateMode mode, double fixedHz) {
		double hz = clampHz(fixedHz);
		Snapshot old = current;
		current = new Snapshot(old.streams(), mode, hz);
		config.update(root -> {
			root.vision.rateMode = mode;
			root.vision.fixedHz = hz;
		});
	}

	public static double clampHz(double hz) {
		if (!Double.isFinite(hz) || hz <= 0) {
			throw new IllegalArgumentException("fixed_hz must be a positive number");
		}
		return Math.max(MIN_HZ, Math.min(MAX_HZ, hz));
	}
}
