package mod.kelvinlby.opencrafter.encoder.pipeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The size guess used before any episode was measured. */
class SizeEstimateTest {
	@Test
	void initialRateCoversTheMeasuredWorstCase() {
		EncodeConfig full = new EncodeConfig();
		full.streams = java.util.List.of("rgb", "flow", "flow_occluded");
		double perFrame = SizeEstimate.initialBytesPerSecond(full) / full.fps;
		assertTrue(perFrame >= 402_000, "measured episodes took up to 402 kB per frame, estimate " + perFrame);
		full.maxSeconds = 60;
		assertEquals(60, SizeEstimate.capturedSeconds(3600, full));
	}
}
