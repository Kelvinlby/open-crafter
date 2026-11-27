package mod.kelvinlby.opencrafter.client.contact.shrink;

import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ShrinkingBlockTest {
	private static final Vector3f DROP = new Vector3f(0.3F, 0.1F, 0.7F);

	@Test
	void startsWhereTheBlockIs() {
		assertEquals(new Vector3f(0.5F, 0.5F, 0.5F), ShrinkingBlock.centre(0.0, DROP));
		assertEquals(1.0, ShrinkingBlock.scale(0.0), 0.0);
	}

	@Test
	void endsTinyOnTheDrop() {
		assertTrue(ShrinkingBlock.centre(1.0, DROP).distance(DROP) < 1.0e-6);
		assertTrue(ShrinkingBlock.scale(1.0) < 0.05);
	}

	@Test
	void movesTowardsTheDropAsItShrinks() {
		double previous = Double.MAX_VALUE;
		for (double t = 0.0; t <= 1.0; t += 0.1) {
			double distance = ShrinkingBlock.centre(t, DROP).distance(DROP);
			assertTrue(distance <= previous + 1.0e-6);
			previous = distance;
		}
	}
}
