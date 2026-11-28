package mod.kelvinlby.opencrafter.encoder.pipeline;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** The marker a run leaves when the disk filled up. */
class PauseMarkerTest {
	@Test
	void pauseMarkerListsBothSidesAndTheCommand() {
		String md = PauseMarker.render(java.util.List.of("a"), java.util.List.of(java.util.Map.of("name", "b", "reason", "full")),
			"./gradlew encode -Pinput=x", 5L << 30);
		assertTrue(md.contains("1 of 2 recordings are encoded"), md);
		assertTrue(md.contains("- `b`: full"), md);
		assertTrue(md.contains("./gradlew encode -Pinput=x"), md);
		assertTrue(md.contains("5.0 GB free"), md);
	}
}
