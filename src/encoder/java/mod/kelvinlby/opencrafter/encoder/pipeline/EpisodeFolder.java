package mod.kelvinlby.opencrafter.encoder.pipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Stream;

/** What is in an episode's output folder before a job runs. */
enum EpisodeFolder {
	/** Nothing there yet. */
	ABSENT,
	/** A finished episode ({@code episode.json} is written last). */
	COMPLETE,
	/** Only episode files, without {@code episode.json}: what a crash or a full disk leaves; safe to delete. */
	UNFINISHED,
	/** Something that is not an episode: never deleted. */
	FOREIGN;

	private static final Set<String> EPISODE_FILES = Set.of("episode.json.tmp", "ffmpeg.log", "flow.ocs", "flow_occluded.ocs", "frames.jsonl.zst");

	static EpisodeFolder of(Path dir, EncodeConfig config) throws IOException {
		if (!Files.exists(dir)) {
			return ABSENT;
		}
		if (Files.isRegularFile(dir.resolve("episode.json"))) {
			return COMPLETE;
		}
		if (!Files.isDirectory(dir)) {
			return FOREIGN;
		}
		try (Stream<Path> entries = Files.list(dir)) {
			boolean onlyEpisodeFiles = entries.allMatch(e -> Files.isRegularFile(e) && isEpisodeFile(e.getFileName().toString(), config));
			return onlyEpisodeFiles ? UNFINISHED : FOREIGN;
		}
	}

	private static boolean isEpisodeFile(String name, EncodeConfig config) {
		return EPISODE_FILES.contains(name) || name.equals(config.rgb.file);
	}
}
