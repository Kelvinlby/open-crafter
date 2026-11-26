package mod.kelvinlby.opencrafter.core.config.handview;

/** Scale, then position, then rotation offsets for one hand or the bare arm. */
public final class HandTransformConfig {
	public Vec3Config scale = Vec3Config.one();
	public Vec3Config position = Vec3Config.zero();
	public Vec3Config rotation = Vec3Config.zero();
}
