package mod.kelvinlby.opencrafter.client.status;

import mod.kelvinlby.opencrafter.api.ModContext;
import mod.kelvinlby.opencrafter.api.ModModule;
import mod.kelvinlby.opencrafter.api.ThreadAffinity;
import mod.kelvinlby.opencrafter.api.sensor.Sensor;

import java.util.Map;

/** Status sensor: resolution, rotation, health, hunger, air, status effects. */
public final class StatusModule implements ModModule {
	private static final String NAME = "status";

	@Override
	public void install(ModContext context) {
		context.sensors().register(new Sensor() {
			@Override
			public String name() {
				return NAME;
			}

			@Override
			public Map<String, Object> read() {
				return StatusReader.read();
			}
		});
		context.handlers().register("status.read", ThreadAffinity.MAIN, args -> StatusReader.read());
	}
}
