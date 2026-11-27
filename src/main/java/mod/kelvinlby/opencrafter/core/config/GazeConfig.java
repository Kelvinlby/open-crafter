package mod.kelvinlby.opencrafter.core.config;

/**
 * Gaze: the rendered first-person camera looks where the body acts. Render-only: input, physics,
 * the player's rotation and everything sent to the server are unchanged.
 */
public final class GazeConfig {
	public static final int MIN_EASE_MILLIS = 20;
	public static final int MAX_EASE_MILLIS = 3000;
	public static final int MIN_HOLD_MILLIS = 0;
	public static final int MAX_HOLD_MILLIS = 5000;

	/** Global toggle: when off, the camera is vanilla. Off by default: whether the view should do this is a research choice. */
	public boolean enabled = false;
	/** The camera follows the rotation the server last received while another mod spoofs it. */
	public boolean followServerAim = true;
	/** The camera turns toward actions off the crosshair that came without any rotation. */
	public boolean lookAtActions = true;
	/** Roughly how long the camera takes to turn onto a target (to within ~10%). */
	public int easeInMillis = 150;
	/** How long the camera stays on a target after the action's outcome (or after the last spoofed tick). */
	public int holdMillis = 300;
	/** Roughly how long the camera takes to turn back to the player's own rotation. */
	public int easeOutMillis = 250;
}
