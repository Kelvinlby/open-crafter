package mod.kelvinlby.opencrafter.api;

/** A feature area (vision, input, inventory, ...) that registers its handlers/sensors. */
public interface ModModule {
	void install(ModContext context);
}
