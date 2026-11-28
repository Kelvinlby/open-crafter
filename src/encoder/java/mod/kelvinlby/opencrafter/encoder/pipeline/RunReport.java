package mod.kelvinlby.opencrafter.encoder.pipeline;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import mod.kelvinlby.opencrafter.encoder.EncoderLog;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Outcome of an encoder run, logged as it goes and written to {@code encode-report.json} at the end. */
final class RunReport {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

	private final List<Map<String, Object>> done = new ArrayList<>();
	private final List<Map<String, Object>> failed = new ArrayList<>();
	private final List<Map<String, Object>> pending = new ArrayList<>();
	private final List<String> skipped = new ArrayList<>();
	private final long started = System.nanoTime();
	private int total;

	synchronized void planned(int recordings) {
		total = recordings;
	}

	synchronized void skipped(String name) {
		skipped.add(name);
	}

	synchronized void done(Job job, double seconds) {
		done.add(Map.of("name", job.name(), "seconds", seconds));
		EncoderLog.LOGGER.info("{} done in {} s ({})", job.name(), Math.round(seconds), progress());
	}

	synchronized void failed(Job job, String reason) {
		failed(job.name(), job.source(), reason);
	}

	synchronized void failed(String name, String source, String reason) {
		failed.add(Map.of("name", name, "source", source, "reason", reason));
		EncoderLog.LOGGER.error("{} FAILED: {} ({})", name, reason, progress());
	}

	/** Left for a later run (the disk is full); not a failure. */
	synchronized void pending(Job job, String reason) {
		pending(job.name(), job.source(), reason);
	}

	synchronized void pending(String name, String source, String reason) {
		pending.add(Map.of("name", name, "source", source, "reason", reason));
		EncoderLog.LOGGER.warn("{} PENDING: {} ({})", name, reason, progress());
	}

	synchronized boolean anyPending() {
		return !pending.isEmpty();
	}

	synchronized boolean anyFailed() {
		return !failed.isEmpty();
	}

	synchronized void write(Path output) throws IOException {
		Map<String, Object> report = new LinkedHashMap<>();
		report.put("recordings", total);
		report.put("done", done);
		report.put("failed", failed);
		report.put("pending", pending);
		report.put("skipped_existing", skipped);
		report.put("wall_seconds", (System.nanoTime() - started) / 1e9);
		Files.writeString(output.resolve("encode-report.json"), GSON.toJson(report));
		EncoderLog.LOGGER.info("Encoder finished: {} done, {} failed, {} pending, {} already encoded", done.size(), failed.size(),
			pending.size(), skipped.size());
	}

	/** Names of every recording whose episode is complete: encoded in this run or before. */
	synchronized List<String> encodedNames() {
		List<String> names = new ArrayList<>(skipped);
		done.forEach(d -> names.add((String) d.get("name")));
		return names.stream().sorted().toList();
	}

	/** Recordings not encoded yet, each with why. */
	synchronized List<Map<String, Object>> notEncoded() {
		List<Map<String, Object>> left = new ArrayList<>(pending);
		left.addAll(failed);
		left.sort(java.util.Comparator.comparing(m -> (String) m.get("name")));
		return left;
	}

	private String progress() {
		return (done.size() + failed.size() + pending.size() + skipped.size()) + "/" + total;
	}
}
