package mod.kelvinlby.opencrafter.client.handview.anim;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AimTurnTest {
	private static final Vector3fc PIVOT = new Vector3f(0.56F, -0.52F, -0.72F);

	@Test
	void aContactOnTheCrosshairTurnsNothing() {
		assertIdentity(AimTurn.toward(PIVOT, new Vector3f(0, 0, -3), 1));
	}

	@Test
	void weightZeroTurnsNothing() {
		assertIdentity(AimTurn.toward(PIVOT, new Vector3f(-2, 0, -3), 0));
	}

	@Test
	void theTurnPointsTheHandAtTheContact() {
		Vector3f contact = new Vector3f(-1, 0.3F, -3);
		Quaternionf turn = AimTurn.toward(PIVOT, contact, 1);
		Vector3f ahead = new Vector3f(0, 0, -contact.length()).sub(PIVOT).normalize();
		Vector3f toContact = new Vector3f(contact).sub(PIVOT).normalize();
		assertTrue(turn.transform(ahead).distance(toContact) < 1e-4, ahead.toString());
	}

	@Test
	void aContactToTheLeftTurnsTheHandLeft() {
		Vector3f forward = AimTurn.toward(PIVOT, new Vector3f(-1, 0, -3), 1).transform(new Vector3f(0, 0, -1));
		assertTrue(forward.x < -0.1F, forward.toString());
	}

	@Test
	void aboutTheEyeTheViewTurnsOntoTheContact() {
		Vector3f contact = new Vector3f(-0.8F, 0.4F, -3);
		Vector3f forward = AimTurn.toward(new Vector3f(), contact, 1).transform(new Vector3f(0, 0, -1));
		assertTrue(forward.distance(new Vector3f(contact).normalize()) < 1e-4, forward.toString());
	}

	@Test
	void farContactsTurnAtMostTheLimit() {
		Quaternionf turn = AimTurn.toward(PIVOT, new Vector3f(-5, 0, 0.5F), 1);
		assertEquals(AimTurn.MAX_RADIANS, turn.angle(), 1e-4);
	}

	@Test
	void weightScalesTheAngle() {
		Vector3f contact = new Vector3f(-1, 0, -3);
		float full = AimTurn.toward(PIVOT, contact, 1).angle();
		assertEquals(full / 2, AimTurn.toward(PIVOT, contact, 0.5F).angle(), 1e-4);
	}

	private static void assertIdentity(Quaternionf turn) {
		assertEquals(0, turn.angle(), 1e-4);
	}
}
