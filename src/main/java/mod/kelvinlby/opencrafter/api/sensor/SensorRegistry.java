package mod.kelvinlby.opencrafter.api.sensor;

import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** All registered non-visual sensors, by name. */
public final class SensorRegistry {
	private final Map<String, Sensor> sensors = new ConcurrentHashMap<>();

	public void register(Sensor sensor) {
		if (sensors.putIfAbsent(sensor.name(), sensor) != null) {
			throw new IllegalStateException("Duplicate sensor: " + sensor.name());
		}
	}

	public Optional<Sensor> find(String name) {
		return Optional.ofNullable(sensors.get(name));
	}

	public Collection<Sensor> all() {
		return sensors.values();
	}
}
