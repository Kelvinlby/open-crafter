package mod.kelvinlby.opencrafter.client.vision;

import mod.kelvinlby.opencrafter.api.Args;
import mod.kelvinlby.opencrafter.api.ThreadAffinity;
import mod.kelvinlby.opencrafter.api.handler.HandlerRegistry;
import mod.kelvinlby.opencrafter.core.config.VisionConfig.RateMode;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Backend ops of the vision sensor; see docs/ops/vision.md. */
final class VisionOps {
	private final VisionControl control;

	VisionOps(VisionControl control) {
		this.control = control;
	}

	void register(HandlerRegistry handlers) {
		handlers.register("vision.configure", ThreadAffinity.ANY, this::configure);
		handlers.register("vision.status", ThreadAffinity.ANY, args -> control.status());
	}

	/** Args (all optional): {@code streams}: list of stream names; {@code rate_mode}; {@code fixed_hz}. */
	private Map<String, Object> configure(Args args) {
		if (args.has("streams")) {
			control.setStreams(parseStreams(args.list("streams")));
		}
		if (args.has("rate_mode") || args.has("fixed_hz")) {
			applyRate(args);
		}
		return control.status();
	}

	private void applyRate(Args args) {
		VisionSettings.Snapshot current = control.settings();
		RateMode mode = args.has("rate_mode") ? RateModes.parse(args.string("rate_mode")) : current.rateMode();
		double hz = args.number("fixed_hz", current.fixedHz());
		control.setRate(mode, hz);
	}

	private static Set<VisionStream> parseStreams(List<?> names) {
		Set<VisionStream> streams = EnumSet.noneOf(VisionStream.class);
		for (Object name : names) {
			streams.add(VisionStream.byWireName(String.valueOf(name)));
		}
		return streams;
	}
}
