package mod.kelvinlby.opencrafter.encoder.format;

/** Connection protocol phase, as stored in a record's flags (bits 1-3). */
public enum Phase {
	HANDSHAKING,
	STATUS,
	LOGIN,
	CONFIGURATION,
	PLAY;

	private static final Phase[] BY_ID = values();

	public static Phase byId(int id) {
		if (id < 0 || id >= BY_ID.length) {
			throw new IllegalArgumentException("Unknown phase id " + id);
		}
		return BY_ID[id];
	}
}
