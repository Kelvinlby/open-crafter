package mod.kelvinlby.opencrafter.client.handview.anim;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TravelTest {
	@Test
	void goesOneWayAndStays() {
		Travel travel = new Travel();
		travel.start(10, 3);
		assertEquals(0, travel.weight(10), 1e-6);
		assertEquals(0.5, travel.weight(11.5), 1e-6);
		assertFalse(travel.arrived(12.9));
		assertTrue(travel.arrived(13));
		assertEquals(1, travel.weight(20), 1e-6);
	}

	@Test
	void restsUntilStarted() {
		Travel travel = new Travel();
		assertEquals(0, travel.weight(5), 1e-6);
		assertFalse(travel.arrived(5));
	}
}
