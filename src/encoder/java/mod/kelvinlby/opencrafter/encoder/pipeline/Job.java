package mod.kelvinlby.opencrafter.encoder.pipeline;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * One recording for one worker: the native-protocol recording to replay and where its episode goes.
 *
 * @param name      episode name (the recording's base name)
 * @param recording recording in the native protocol (the original, or its translated copy)
 * @param source    the recording as given, for provenance
 * @param output    episode directory to write
 * @param config    capture settings
 */
public record Job(String name, String recording, String source, String output, EncodeConfig config) {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
	/** System property naming the job file of a worker client. */
	public static final String PROPERTY = "opencrafter.encoder.job";

	public static Job read(Path file) throws IOException {
		return GSON.fromJson(Files.readString(file), Job.class);
	}

	public void write(Path file) throws IOException {
		Files.createDirectories(file.toAbsolutePath().getParent());
		Files.writeString(file, GSON.toJson(this));
	}

	public Path recordingPath() {
		return Path.of(recording);
	}

	public Path outputPath() {
		return Path.of(output);
	}
}
