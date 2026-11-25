package mod.kelvinlby.opencrafter.client.vision.record;

/** Which camera a recorded object was rendered with. */
public enum RecordPhase {
	/** The level: world projection and view. */
	WORLD,
	/** The first-person hands and held items: hand projection, camera-attached. */
	HAND
}
