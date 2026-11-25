package mod.kelvinlby.opencrafter.api;

import mod.kelvinlby.opencrafter.api.frame.FramePublisher;
import mod.kelvinlby.opencrafter.api.handler.HandlerRegistry;
import mod.kelvinlby.opencrafter.api.sensor.SensorRegistry;
import mod.kelvinlby.opencrafter.core.config.ConfigStore;

import java.util.concurrent.Executor;

/** Everything a module needs to plug into the mod; game-agnostic. */
public record ModContext(
	HandlerRegistry handlers,
	SensorRegistry sensors,
	EventSink events,
	BackendLifecycle lifecycle,
	FramePublisher frames,
	ConfigStore config,
	Executor mainThread
) {
}
