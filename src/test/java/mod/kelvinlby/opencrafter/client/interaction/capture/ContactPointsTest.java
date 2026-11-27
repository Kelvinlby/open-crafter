package mod.kelvinlby.opencrafter.client.interaction.capture;

import mod.kelvinlby.opencrafter.client.interaction.event.Aim;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ContactPointsTest {
	private static final double EPSILON = 1.0E-6;
	/** Yaw 0 looks south (+z). */
	private static final Aim SOUTH = new Aim(0.0F, 0.0F);

	@Test
	void faceCentreSitsHalfABlockOutFromTheBlockCentre() {
		assertVec(new Vec3(0.5, 1.0, 0.5), ContactPoints.faceCentre(BlockPos.ZERO, Direction.UP));
		assertVec(new Vec3(0.0, 0.5, 0.5), ContactPoints.faceCentre(BlockPos.ZERO, Direction.WEST));
	}

	@Test
	void aRayThatHitsTheBoxContactsWhereItEnters() {
		AABB box = new AABB(-0.5, 0.0, 3.0, 0.5, 2.0, 4.0);
		assertVec(new Vec3(0.0, 1.0, 3.0), ContactPoints.onBox(box, new Vec3(0.0, 1.0, 0.0), SOUTH));
	}

	@Test
	void aRayThatMissesContactsTheBoxPointNearestToIt() {
		AABB box = new AABB(2.0, 0.0, 3.0, 3.0, 2.0, 4.0);
		assertVec(new Vec3(2.0, 1.0, 3.5), ContactPoints.onBox(box, new Vec3(0.0, 1.0, 0.0), SOUTH));
	}

	@Test
	void aBoxBehindTheEyeIsNotProjectedBackwards() {
		AABB box = new AABB(-0.5, 0.0, -4.0, 0.5, 2.0, -3.0);
		assertVec(new Vec3(0.0, 1.0, -3.0), ContactPoints.closestToRay(box, new Vec3(0.0, 1.0, 0.0), SOUTH.direction()));
	}

	@Test
	void alongRayWalksTheAimDirection() {
		assertVec(new Vec3(0.0, 1.0, 3.0), ContactPoints.alongRay(new Vec3(0.0, 1.0, 0.0), SOUTH, 3.0));
	}

	private static void assertVec(Vec3 expected, Vec3 actual) {
		assertEquals(expected.x, actual.x, EPSILON, "x");
		assertEquals(expected.y, actual.y, EPSILON, "y");
		assertEquals(expected.z, actual.z, EPSILON, "z");
	}
}
