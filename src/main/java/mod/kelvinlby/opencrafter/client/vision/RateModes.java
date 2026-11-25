package mod.kelvinlby.opencrafter.client.vision;

import mod.kelvinlby.opencrafter.core.config.VisionConfig.RateMode;

import java.util.Locale;

/** Wire names of {@link RateMode}: {@code tick}, {@code fixed_hz}, {@code max_fps}. */
public final class RateModes {
	private RateModes() {
	}

	public static String wireName(RateMode mode) {
		return mode.name().toLowerCase(Locale.ROOT);
	}

	public static RateMode parse(String name) {
		return switch (name.toLowerCase(Locale.ROOT)) {
			case "tick" -> RateMode.TICK;
			case "fixed_hz", "hz" -> RateMode.FIXED_HZ;
			case "max_fps", "fps" -> RateMode.MAX_FPS;
			default -> throw new IllegalArgumentException("Unknown rate_mode: " + name + " (tick, fixed_hz, max_fps)");
		};
	}
}
