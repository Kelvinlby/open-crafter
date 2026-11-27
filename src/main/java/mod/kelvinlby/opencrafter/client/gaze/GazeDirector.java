package mod.kelvinlby.opencrafter.client.gaze;

import mod.kelvinlby.opencrafter.client.gaze.anim.Angles;
import mod.kelvinlby.opencrafter.client.gaze.anim.GazeOffset;
import mod.kelvinlby.opencrafter.client.gaze.anim.HeldTarget;
import mod.kelvinlby.opencrafter.client.interaction.event.Aim;
import mod.kelvinlby.opencrafter.core.config.ConfigStore;
import mod.kelvinlby.opencrafter.core.config.GazeConfig;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * Turns the rendered camera, once per frame: eases its offset from the player's own rotation toward
 * the active target and back home. Being an offset, the player's own turning shows at once and the
 * camera is exactly vanilla once home. Render thread only.
 */
final class GazeDirector {
	/** Longer gaps (loading, a stalled window) count as this much, so the camera never jumps. */
	private static final double MAX_FRAME_SECONDS = 0.25;

	private final ConfigStore config;
	private final HeldTarget<GazeTarget> targets;
	private final GazeOffset offset = new GazeOffset();
	private long lastFrameNanos = Util.getNanos();

	GazeDirector(ConfigStore config, HeldTarget<GazeTarget> targets) {
		this.config = config;
		this.targets = targets;
	}

	/** The rotation to render instead of the player's own {@code own}, or null to keep vanilla's. */
	@Nullable Aim cameraAim(Aim own, Vec3 eye, long nowNanos) {
		double seconds = advanceClock(nowNanos);
		GazeConfig settings = config.get().gaze;
		GazeTarget target = activeTarget(settings, nowNanos);
		if (target != null) {
			easeToward(target.aimFrom(eye), own, settings, seconds);
		} else {
			offset.easeHome(seconds(settings.easeOutMillis, GazeConfig.MIN_EASE_MILLIS, GazeConfig.MAX_EASE_MILLIS), seconds);
		}
		return offset.isHome() ? null : rendered(own);
	}

	/** The camera does not show the player's eyes this frame (third person, sleeping, spectating). */
	void outOfView(long nowNanos) {
		advanceClock(nowNanos);
		offset.reset();
	}

	/** Joining or leaving a world. */
	void reset() {
		offset.reset();
		targets.clear();
	}

	private @Nullable GazeTarget activeTarget(GazeConfig settings, long nowNanos) {
		if (!settings.enabled) {
			return null;
		}
		long holdNanos = (long) (seconds(settings.holdMillis, GazeConfig.MIN_HOLD_MILLIS, GazeConfig.MAX_HOLD_MILLIS) * 1.0E9);
		GazeTarget target = targets.active(nowNanos, holdNanos);
		return target != null && target.enabledIn(settings) ? target : null;
	}

	private void easeToward(Aim wanted, Aim own, GazeConfig settings, double seconds) {
		double yaw = Angles.wrapDegrees(wanted.yaw() - own.yaw());
		double pitch = Angles.clampPitch(wanted.pitch()) - own.pitch();
		offset.easeToward(yaw, pitch, seconds(settings.easeInMillis, GazeConfig.MIN_EASE_MILLIS, GazeConfig.MAX_EASE_MILLIS), seconds);
	}

	private Aim rendered(Aim own) {
		return new Aim((float) (own.yaw() + offset.yaw()), (float) Angles.clampPitch(own.pitch() + offset.pitch()));
	}

	private double advanceClock(long nowNanos) {
		double seconds = (nowNanos - lastFrameNanos) / 1.0E9;
		lastFrameNanos = nowNanos;
		return Mth.clamp(seconds, 0.0, MAX_FRAME_SECONDS);
	}

	private static double seconds(int millis, int min, int max) {
		return Mth.clamp(millis, min, max) / 1000.0;
	}
}
