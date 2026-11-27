package mod.kelvinlby.opencrafter.encoder.translate;

import mod.kelvinlby.opencrafter.encoder.format.McrecHeader;
import mod.kelvinlby.opencrafter.encoder.format.Rec;
import mod.kelvinlby.opencrafter.encoder.format.Recording;
import mod.kelvinlby.opencrafter.encoder.format.RecordingReader;
import net.minecraft.SharedConstants;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Translates real recordings ({@code -PsampleRecordings=DIR}) and reads the result back. */
class NormalizeSampleRecordingsTest {
	@Test
	void translatesEverySampleToTheNativeProtocol() throws IOException {
		String dir = System.getProperty("encoder.sampleRecordings");
		Assumptions.assumeTrue(dir != null, "no -PsampleRecordings given");
		ViaRuntime.ensureStarted(Path.of("via"));
		int nativeProtocol = SharedConstants.getProtocolVersion();
		for (Path path : samples(Path.of(dir))) {
			Recording recording = Recording.open(path);
			Path target = Path.of("normalized", recording.baseName() + ".mcrec");
			long started = System.nanoTime();
			ProtocolNormalizer.Result result = ProtocolNormalizer.normalize(recording, target, nativeProtocol, "26.3");
			System.out.printf("%s: %d in, %d out, %d failed in %.1fs%n  %s%n", recording.baseName(), result.packetsIn(), result.packetsOut(),
				result.failed(), (System.nanoTime() - started) / 1e9, String.join("\n  ", result.warnings()));
			assertEquals(0, result.failed(), "untranslatable packets");
			assertReadsBack(target, nativeProtocol, result.packetsOut());
			if (ProtocolNormalizer.needsTranslation(recording.reader().header(), nativeProtocol)) {
				assertIdsRemapped(recording, target);
			}
		}
	}

	/** Packet ids differ between protocols: most PLAY packets must come out with other leading bytes. */
	private static void assertIdsRemapped(Recording original, Path normalized) throws IOException {
		java.util.Map<Integer, Integer> before = idHistogram(original.reader());
		java.util.Map<Integer, Integer> after = idHistogram(Recording.open(normalized).reader());
		System.out.println("  S2C PLAY ids before: " + top(before) + "\n  after: " + top(after));
		assertTrue(!before.equals(after), "packet ids unchanged: Via did not translate");
	}

	private static java.util.Map<Integer, Integer> idHistogram(RecordingReader reader) throws IOException {
		java.util.Map<Integer, Integer> counts = new java.util.TreeMap<>();
		try (reader) {
			for (Rec rec = reader.next(); rec != null; rec = reader.next()) {
				if (!rec.gap() && !rec.c2s() && rec.phase() == mod.kelvinlby.opencrafter.encoder.format.Phase.PLAY) {
					counts.merge(rec.packetId(), 1, Integer::sum);
				}
			}
		}
		return counts;
	}

	private static String top(java.util.Map<Integer, Integer> counts) {
		return counts.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).limit(8)
			.map(e -> "0x" + Integer.toHexString(e.getKey()) + "=" + e.getValue()).toList().toString();
	}

	private static void assertReadsBack(Path normalized, int nativeProtocol, long expectedPackets) throws IOException {
		try (RecordingReader reader = Recording.open(normalized).reader()) {
			McrecHeader header = reader.header();
			assertEquals(nativeProtocol, header.protocolVersion());
			long packets = 0;
			long lastTime = 0;
			for (Rec rec = reader.next(); rec != null; rec = reader.next()) {
				assertTrue(rec.timeMicros() >= lastTime, "time runs backwards");
				lastTime = rec.timeMicros();
				packets += rec.gap() ? 0 : 1;
			}
			assertEquals(expectedPackets, packets);
			assertTrue(reader.warnings().isEmpty(), reader.warnings().toString());
		}
	}

	private static List<Path> samples(Path dir) throws IOException {
		try (Stream<Path> files = Files.list(dir)) {
			return files.filter(Recording::looksLikeRecording).sorted().toList();
		}
	}
}
