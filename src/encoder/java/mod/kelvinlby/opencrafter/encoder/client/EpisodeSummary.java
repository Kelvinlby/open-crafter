package mod.kelvinlby.opencrafter.encoder.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mod.kelvinlby.opencrafter.encoder.format.McrecHeader;
import mod.kelvinlby.opencrafter.encoder.output.EpisodeWriter;
import mod.kelvinlby.opencrafter.encoder.pipeline.Job;
import net.minecraft.SharedConstants;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/** {@code episode.json}: what was replayed, how, and what came out. Written last, so its presence means the episode is complete. */
final class EpisodeSummary {
	static final String FILE = "episode.json";
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().serializeSpecialFloatingPointValues().create();

	private EpisodeSummary() {
	}

	static void write(Job job, McrecHeader header, ReplayStats stats, long s2cDecodeErrors, long c2sDecodeErrors, long gaps, EpisodeWriter episode)
		throws IOException {
		JsonObject summary = new JsonObject();
		summary.addProperty("format", "open-crafter-episode/1");
		summary.addProperty("name", job.name());
		summary.addProperty("source", job.source());
		summary.add("recording", recordingInfo(header));
		summary.addProperty("replayed_with", SharedConstants.getCurrentVersion().name());
		summary.add("config", GSON.toJsonTree(job.config()));
		summary.add("stats", GSON.toJsonTree(stats.toMap()));
		JsonObject problems = new JsonObject();
		problems.addProperty("s2c_decode_errors", s2cDecodeErrors);
		problems.addProperty("c2s_decode_errors", c2sDecodeErrors);
		problems.addProperty("recording_gaps", gaps);
		summary.add("problems", problems);
		summary.add("files", GSON.toJsonTree(episode.storedBytes()));
		Path target = job.outputPath().resolve(FILE);
		Path temp = target.resolveSibling(FILE + ".tmp");
		Files.writeString(temp, GSON.toJson(summary));
		Files.move(temp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}

	private static JsonObject recordingInfo(McrecHeader header) {
		JsonObject info = new JsonObject();
		for (String key : new String[]{"playerName", "playerUuid", "minecraftVersion", "serverBrand", "sessionId", "sessionStartEpochMillis",
			"recordingId", "connectionId", "startReason", "translatedFrom"}) {
			JsonElement value = header.json().get(key);
			if (value != null) {
				info.add(key, value);
			}
		}
		return info;
	}
}
