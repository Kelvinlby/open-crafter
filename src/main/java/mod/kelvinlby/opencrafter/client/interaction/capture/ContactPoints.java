package mod.kelvinlby.opencrafter.client.interaction.capture;

import mod.kelvinlby.opencrafter.client.interaction.event.Aim;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

/** Where an action meets its target, in world space. Pure geometry. */
final class ContactPoints {
	private ContactPoints() {
	}

	/** The centre of a block's face. */
	static Vec3 faceCentre(BlockPos pos, Direction face) {
		return Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(0.5));
	}

	/** An entity interaction's location is sent relative to the entity's position. */
	static Vec3 entityRelative(Entity entity, Vec3 relative) {
		return entity.position().add(relative);
	}

	/** Where the aim ray enters the box, or the box point closest to the ray when it misses. */
	static Vec3 onBox(AABB box, Vec3 eye, Aim aim) {
		Vec3 direction = aim.direction();
		double reach = Math.sqrt(box.distanceToSqr(eye)) + box.getSize() * 2.0;
		return box.clip(eye, eye.add(direction.scale(reach))).orElseGet(() -> closestToRay(box, eye, direction));
	}

	/** A point {@code distance} along the aim ray. */
	static Vec3 alongRay(Vec3 eye, Aim aim, double distance) {
		return eye.add(aim.direction().scale(distance));
	}

	/** The ray point nearest the box centre, clamped into the box. */
	static Vec3 closestToRay(AABB box, Vec3 eye, Vec3 direction) {
		double t = Math.max(0.0, box.getCenter().subtract(eye).dot(direction));
		Vec3 onRay = eye.add(direction.scale(t));
		return new Vec3(
			Mth.clamp(onRay.x, box.minX, box.maxX),
			Mth.clamp(onRay.y, box.minY, box.maxY),
			Mth.clamp(onRay.z, box.minZ, box.maxZ)
		);
	}
}
