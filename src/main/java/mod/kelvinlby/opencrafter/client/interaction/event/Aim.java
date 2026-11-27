package mod.kelvinlby.opencrafter.client.interaction.event;

import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/** A rotation in degrees, Minecraft's convention (yaw 0 = south, pitch -90 = up). */
public record Aim(float yaw, float pitch) {
	public Vec3 direction() {
		return Vec3.directionFromRotation(pitch, yaw);
	}

	/** The larger of the yaw and pitch differences, in degrees. */
	public float degreesFrom(Aim other) {
		return Math.max(Math.abs(Mth.wrapDegrees(yaw - other.yaw)), Math.abs(pitch - other.pitch));
	}
}
