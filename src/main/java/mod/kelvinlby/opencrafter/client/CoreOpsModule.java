package mod.kelvinlby.opencrafter.client;

import mod.kelvinlby.opencrafter.api.Args;
import mod.kelvinlby.opencrafter.api.ModContext;
import mod.kelvinlby.opencrafter.api.ModModule;
import mod.kelvinlby.opencrafter.api.ThreadAffinity;
import mod.kelvinlby.opencrafter.api.handler.HandlerRegistry;
import mod.kelvinlby.opencrafter.api.sensor.Sensor;
import mod.kelvinlby.opencrafter.api.sensor.SensorRegistry;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Introspection and generic sensor ops: ops.list, sensors.list, sensor.read. */
final class CoreOpsModule implements ModModule {
	@Override
	public void install(ModContext context) {
		HandlerRegistry handlers = context.handlers();
		SensorRegistry sensors = context.sensors();
		handlers.register("ops.list", ThreadAffinity.ANY, args -> listOps(handlers));
		handlers.register("sensors.list", ThreadAffinity.ANY, args -> sensors.all().stream().map(Sensor::name).sorted().toList());
		handlers.register("sensor.read", ThreadAffinity.MAIN, args -> readSensors(sensors, args));
	}

	private static List<String> listOps(HandlerRegistry handlers) {
		return handlers.all().stream().map(HandlerRegistry.Entry::op).sorted().toList();
	}

	/** {@code names}: optional list of sensor names; default reads all. */
	private static Map<String, Object> readSensors(SensorRegistry sensors, Args args) {
		List<?> names = args.has("names") ? args.list("names") : sensors.all().stream().map(Sensor::name).toList();
		Map<String, Object> result = new HashMap<>();
		for (Object name : names) {
			Sensor sensor = sensors.find(String.valueOf(name))
				.orElseThrow(() -> new IllegalArgumentException("Unknown sensor: " + name));
			result.put(sensor.name(), sensor.read());
		}
		return result;
	}
}
