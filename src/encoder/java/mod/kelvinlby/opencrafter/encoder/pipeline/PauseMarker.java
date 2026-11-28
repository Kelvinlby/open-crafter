package mod.kelvinlby.opencrafter.encoder.pipeline;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * {@code ENCODE-PAUSED.md} in the output folder: written when a run stopped because the disk was
 * full, removed by a run that left nothing pending. Says what is encoded, what is not, and how to
 * continue.
 */
final class PauseMarker {
	static final String FILE = "ENCODE-PAUSED.md";

	private PauseMarker() {
	}

	/** Writes the marker if the run left recordings pending, otherwise removes a stale one. */
	static void update(Path output, RunReport report, String resumeCommand, long freeBytes) throws IOException {
		Path marker = output.resolve(FILE);
		if (!report.anyPending()) {
			Files.deleteIfExists(marker);
			return;
		}
		Files.writeString(marker, render(report.encodedNames(), report.notEncoded(), resumeCommand, freeBytes));
	}

	static String render(List<String> encoded, List<Map<String, Object>> notEncoded, String resumeCommand, long freeBytes) {
		StringBuilder md = new StringBuilder();
		md.append("# Encoding paused: the output disk is full\n\n");
		md.append(ZonedDateTime.now().format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)).append(": ")
			.append(encoded.size()).append(" of ").append(encoded.size() + notEncoded.size()).append(" recordings are encoded, ")
			.append(notEncoded.size()).append(" are not yet. ")
			.append(String.format(java.util.Locale.ROOT, "%.1f GB free.%n%n", freeBytes / (double) (1L << 30)));
		md.append("Every episode folder here is complete (it has `episode.json`); unfinished ones were deleted.\n\n");
		md.append("## Resume\n\n");
		md.append("Make room (or move this folder to larger storage), then run the same command again. Finished episodes are skipped:\n\n");
		md.append("```bash\n").append(resumeCommand.isBlank() ? "./gradlew encode <same options as before>" : resumeCommand).append("\n```\n\n");
		md.append("## Not encoded yet (").append(notEncoded.size()).append(")\n\n");
		for (Map<String, Object> left : notEncoded) {
			md.append("- `").append(left.get("name")).append("`: ").append(left.get("reason")).append('\n');
		}
		md.append("\n## Encoded (").append(encoded.size()).append(")\n\n");
		for (String name : encoded) {
			md.append("- `").append(name).append("`\n");
		}
		return md.toString();
	}
}
