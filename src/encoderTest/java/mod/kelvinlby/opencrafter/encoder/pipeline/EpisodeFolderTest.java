package mod.kelvinlby.opencrafter.encoder.pipeline;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Only episode.json makes an episode complete; only folders of episode files are ever deleted. */
class EpisodeFolderTest {
	private final EncodeConfig config = new EncodeConfig();

	@Test
	void classifiesWhatARunLeaves() throws IOException {
		Path root = Files.createDirectories(Path.of("episode-folders")).toAbsolutePath();
		assertEquals(EpisodeFolder.ABSENT, EpisodeFolder.of(root.resolve("none"), config));

		Path unfinished = Files.createDirectories(root.resolve("unfinished"));
		Files.writeString(unfinished.resolve("rgb.mkv"), "x");
		Files.writeString(unfinished.resolve("flow.ocs"), "x");
		Files.writeString(unfinished.resolve("episode.json.tmp"), "x");
		assertEquals(EpisodeFolder.UNFINISHED, EpisodeFolder.of(unfinished, config));
		assertEquals(EpisodeFolder.UNFINISHED, EpisodeFolder.of(Files.createDirectories(root.resolve("empty")), config));

		Path complete = Files.createDirectories(root.resolve("complete"));
		Files.writeString(complete.resolve("rgb.mkv"), "x");
		Files.writeString(complete.resolve("episode.json"), "{}");
		assertEquals(EpisodeFolder.COMPLETE, EpisodeFolder.of(complete, config));

		Path foreign = Files.createDirectories(root.resolve("foreign"));
		Files.writeString(foreign.resolve("recording.tar"), "x");
		assertEquals(EpisodeFolder.FOREIGN, EpisodeFolder.of(foreign, config));
		Path nested = Files.createDirectories(root.resolve("nested").resolve("sub"));
		assertEquals(EpisodeFolder.FOREIGN, EpisodeFolder.of(nested.getParent(), config));
	}
}
