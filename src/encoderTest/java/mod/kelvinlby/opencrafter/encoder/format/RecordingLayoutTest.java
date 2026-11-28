package mod.kelvinlby.opencrafter.encoder.format;

import com.google.gson.JsonObject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The 26.3 recorder's layout: one file per recording, continued by {@code _sN} files after a size split. */
class RecordingLayoutTest {
	@Test
	void readsAFileAndItsContinuationsAsOneStream() throws IOException {
		Path dir = Files.createDirectories(Path.of("layout")).toAbsolutePath();
		Path first = dir.resolve("2026-11-25_17-25-38_Notch_2bfa919d.mcrec");
		Path second = dir.resolve("2026-11-25_17-25-38_Notch_2bfa919d_s1.mcrec");
		write(first, 0, 100);
		write(second, 100, 50);

		assertTrue(Recording.looksLikeRecording(first));
		assertFalse(Recording.looksLikeRecording(second), "a continuation is not a recording of its own");
		Recording recording = Recording.open(first);
		assertEquals("2026-11-25_17-25-38_Notch_2bfa919d", recording.baseName());
		List<Rec> records = readAll(recording);
		assertEquals(150, records.size());
		for (int i = 0; i < records.size(); i++) {
			assertEquals(i * 1000L, records.get(i).timeMicros());
			assertEquals(i % 2 == 1, records.get(i).c2s());
			assertEquals(i, records.get(i).data()[1] & 0xFF);
		}
	}

	@Test
	void sessionDurationComesFromSessionJson() throws IOException {
		Path dir = Files.createDirectories(Path.of("layout-session")).toAbsolutePath();
		write(dir.resolve("0000.mcrec"), 0, 10);
		Files.writeString(dir.resolve("session.json"),
			"{\"startedEpochMillis\": 1000, \"endedEpochMillis\": 61500, \"segments\": [{\"file\": \"0000.mcrec\"}]}");
		assertEquals(60_500L, Recording.open(dir).durationMillis().orElseThrow());
		assertEquals(-1L, Recording.sessionDurationMillis("{\"startedEpochMillis\": 1000}"));
		assertTrue(Recording.open(dir.resolve("0000.mcrec")).durationMillis().isEmpty(), "a bare .mcrec has no session length");
	}

	private static void write(Path file, int from, int count) throws IOException {
		JsonObject json = new JsonObject();
		json.addProperty("formatVersion", 1);
		json.addProperty("serverProtocolVersion", 777);
		try (SegmentWriter writer = new SegmentWriter(file, new McrecHeader(json))) {
			for (int i = from; i < from + count; i++) {
				writer.write(Rec.packet(i % 2 == 1, Phase.PLAY, i * 1000L, new byte[]{0x05, (byte) i}));
			}
		}
	}

	private static List<Rec> readAll(Recording recording) throws IOException {
		List<Rec> records = new ArrayList<>();
		try (RecordingReader reader = recording.reader()) {
			for (Rec rec = reader.next(); rec != null; rec = reader.next()) {
				records.add(rec);
			}
			assertTrue(reader.warnings().isEmpty(), reader.warnings().toString());
		}
		return records;
	}
}
