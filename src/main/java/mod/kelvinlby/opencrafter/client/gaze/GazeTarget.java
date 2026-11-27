package mod.kelvinlby.opencrafter.client.gaze;

import mod.kelvinlby.opencrafter.client.gaze.anim.Angles;
import mod.kelvinlby.opencrafter.client.interaction.event.Aim;
import mod.kelvinlby.opencrafter.core.config.GazeConfig;
import net.minecraft.world.phys.Vec3;

/** What the gaze looks at: a rotation the server received, or a world point an action touched. */
sealed interface GazeTarget {
	/** The rotation that looks at this target from {@code eye}. */
	Aim aimFrom(Vec3 eye);

	/** Whether the settings let the camera follow this kind of target. */
	boolean enabledIn(GazeConfig config);

	/** A rotation the server received that differs from the player's own (another mod spoofed it). */
	record ServerAim(Aim aim) implements GazeTarget {
		@Override
		public Aim aimFrom(Vec3 eye) {
			return aim;
		}

		@Override
		public boolean enabledIn(GazeConfig config) {
			return config.followServerAim;
		}
	}

	/** Where an action off the crosshair met its target, sent without any rotation. */
	record Contact(Vec3 point) implements GazeTarget {
		@Override
		public Aim aimFrom(Vec3 eye) {
			Vec3 to = point.subtract(eye);
			return new Aim((float) Angles.yawTowards(to.x, to.z), (float) Angles.pitchTowards(to.x, to.y, to.z));
		}

		@Override
		public boolean enabledIn(GazeConfig config) {
			return config.lookAtActions;
		}
	}
}
