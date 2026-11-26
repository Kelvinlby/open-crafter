package mod.kelvinlby.opencrafter.core.config.handview;

/** The eating and drinking pose. */
public final class EatingConfig {
	public boolean disableAnimation = false;
	/** Offset of the pose. X is right, Y is up, Z is towards the camera. */
	public Vec3Config offset = Vec3Config.zero();
	/** Size of the food or potion while it is eaten. */
	public double scale = 1.0;
	/** Up-and-down bob, relative to vanilla. */
	public double bob = 1.0;
}
