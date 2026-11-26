package mod.kelvinlby.opencrafter.core.config.handview;

/** A persisted x/y/z triple (position, rotation in degrees, or scale). */
public final class Vec3Config {
	public double x;
	public double y;
	public double z;

	public Vec3Config() {
	}

	public Vec3Config(double x, double y, double z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}

	public static Vec3Config zero() {
		return new Vec3Config(0, 0, 0);
	}

	public static Vec3Config one() {
		return new Vec3Config(1, 1, 1);
	}
}
