package mod.kelvinlby.opencrafter.api.sensor;

import java.util.Map;

/**
 * A non-visual sensor (status today; sound and others later).
 * {@link #read()} is always invoked on the game main thread.
 */
public interface Sensor {
	String name();

	Map<String, Object> read();
}
