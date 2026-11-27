package mod.kelvinlby.opencrafter.client.gaze.anim;

/** Angle helpers in degrees, Minecraft's convention (yaw 0 = +z / south, pitch -90 = up). */
public final class Angles {
	public static final double MAX_PITCH = 90.0;

	private Angles() {
	}

	/** The same angle in [-180, 180). */
	public static double wrapDegrees(double degrees) {
		double wrapped = (degrees + 180.0) % 360.0;
		return (wrapped < 0.0 ? wrapped + 360.0 : wrapped) - 180.0;
	}

	/** The representation of {@code angle} closest to {@code reference}: turning there takes the shortest way. */
	public static double nearestEquivalent(double angle, double reference) {
		return reference + wrapDegrees(angle - reference);
	}

	public static double clampPitch(double pitch) {
		return Math.max(-MAX_PITCH, Math.min(MAX_PITCH, pitch));
	}

	/** Yaw of a direction; 0 straight up or down, where yaw is undefined. */
	public static double yawTowards(double dx, double dz) {
		return dx == 0.0 && dz == 0.0 ? 0.0 : Math.toDegrees(Math.atan2(-dx, dz));
	}

	/** Pitch of a direction (positive looks down). */
	public static double pitchTowards(double dx, double dy, double dz) {
		return Math.toDegrees(Math.atan2(-dy, Math.sqrt(dx * dx + dz * dz)));
	}
}
