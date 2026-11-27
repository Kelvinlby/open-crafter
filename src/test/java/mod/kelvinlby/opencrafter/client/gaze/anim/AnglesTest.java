package mod.kelvinlby.opencrafter.client.gaze.anim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AnglesTest {
	private static final double EPS = 1.0E-9;

	@Test
	void wrapsIntoHalfOpenTurn() {
		assertEquals(0.0, Angles.wrapDegrees(360.0), EPS);
		assertEquals(-180.0, Angles.wrapDegrees(180.0), EPS);
		assertEquals(-170.0, Angles.wrapDegrees(190.0), EPS);
		assertEquals(170.0, Angles.wrapDegrees(-190.0), EPS);
		assertEquals(10.0, Angles.wrapDegrees(730.0), EPS);
		assertEquals(-10.0, Angles.wrapDegrees(-730.0), EPS);
	}

	@Test
	void nearestEquivalentTakesTheShortWayAcrossTheSeam() {
		assertEquals(181.0, Angles.nearestEquivalent(-179.0, 179.0), EPS);
		assertEquals(-181.0, Angles.nearestEquivalent(179.0, -179.0), EPS);
		assertEquals(725.0, Angles.nearestEquivalent(5.0, 720.0), EPS);
		assertEquals(-5.0, Angles.nearestEquivalent(355.0, 0.0), EPS);
	}

	@Test
	void directionsMatchMinecraftConvention() {
		// yaw 0 = +z (south), 90 = -x (west), -90 = +x (east), 180 = -z (north)
		assertEquals(0.0, Angles.yawTowards(0.0, 1.0), EPS);
		assertEquals(90.0, Angles.yawTowards(-1.0, 0.0), EPS);
		assertEquals(-90.0, Angles.yawTowards(1.0, 0.0), EPS);
		assertEquals(180.0, Math.abs(Angles.yawTowards(0.0, -1.0)), EPS);
		assertEquals(45.0, Angles.pitchTowards(0.0, -1.0, 1.0), EPS);
		assertEquals(-90.0, Angles.pitchTowards(0.0, 1.0, 0.0), EPS);
	}

	@Test
	void roundTripsMinecraftDirectionFromRotation() {
		double yaw = 123.0;
		double pitch = -20.0;
		double yawRad = Math.toRadians(yaw);
		double pitchRad = Math.toRadians(pitch);
		// Vec3.directionFromRotation
		double x = -Math.sin(yawRad) * Math.cos(pitchRad);
		double y = -Math.sin(pitchRad);
		double z = Math.cos(yawRad) * Math.cos(pitchRad);
		assertEquals(yaw, Angles.yawTowards(x, z), 1.0E-6);
		assertEquals(pitch, Angles.pitchTowards(x, y, z), 1.0E-6);
	}

	@Test
	void clampsPitch() {
		assertEquals(90.0, Angles.clampPitch(120.0), EPS);
		assertEquals(-90.0, Angles.clampPitch(-91.0), EPS);
		assertEquals(12.5, Angles.clampPitch(12.5), EPS);
	}
}
