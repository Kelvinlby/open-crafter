package mod.kelvinlby.opencrafter.client.gaze;

import mod.kelvinlby.opencrafter.client.interaction.event.Aim;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;

/** Entry point for the gaze camera mixin; vanilla until {@link GazeModule} is installed. Render thread only. */
public final class GazeHooks {
	private static @Nullable GazeDirector director;

	private GazeHooks() {
	}

	static void install(GazeDirector installed) {
		director = installed;
	}

	/**
	 * Vanilla just placed the camera at the camera entity's eyes for this frame; returns the rotation
	 * to render instead, or null to keep vanilla's. Only the first-person view of the local player turns.
	 */
	public static @Nullable Aim cameraAim(Camera camera) {
		if (director == null) {
			return null;
		}
		long now = Util.getNanos();
		if (!showsOwnEyes(camera)) {
			director.outOfView(now);
			return null;
		}
		return director.cameraAim(new Aim(camera.yRot(), camera.xRot()), camera.position(), now);
	}

	private static boolean showsOwnEyes(Camera camera) {
		LocalPlayer player = Minecraft.getInstance().player;
		return player != null && camera.entity() == player && !camera.isDetached() && !player.isSleeping();
	}
}
