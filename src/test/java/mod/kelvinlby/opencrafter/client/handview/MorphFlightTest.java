package mod.kelvinlby.opencrafter.client.handview;

import net.minecraft.world.InteractionHand;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MorphFlightTest {
	private static final double DURATION = 3;

	@Test
	void settlesTheTickAfterArrivingAndEndsRightAwayWithoutFlow() {
		MorphFlight flight = flight();
		flight.tick(3, false);
		assertFalse(flight.travelling());
		assertFalse(flight.done());
		assertFalse(flight.settled(3.5));
		assertTrue(flight.settled(4));
		flight.tick(4, false);
		assertTrue(flight.done());
	}

	@Test
	void staysOnItsBlockUntilARecordedFrameSawItSettled() {
		MorphFlight flight = flight();
		flight.tick(3, true);
		flight.tick(4, true);
		assertFalse(flight.done());
		flight.noteSettledRecorded();
		flight.tick(5, true);
		assertTrue(flight.done());
	}

	@Test
	void givesUpWaitingForARecordedFrame() {
		MorphFlight flight = flight();
		flight.tick(3, true);
		flight.tick(7, true);
		assertFalse(flight.done());
		flight.tick(8, true);
		assertTrue(flight.done());
	}

	private static MorphFlight flight() {
		return new MorphFlight(null, null, InteractionHand.MAIN_HAND, new Matrix4f(), 0, DURATION);
	}
}
