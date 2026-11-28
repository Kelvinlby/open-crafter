package mod.kelvinlby.opencrafter.encoder.format;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * A recording on disk, in any layout the recorders produce:
 * <ul>
 *   <li>a session tar ({@code session.json} + {@code 0000.mcrec}, ...; the 1.21.11 recorder),</li>
 *   <li>a directory of loose segments (an extracted tar or an open {@code .part} session),</li>
 *   <li>a single {@code .mcrec} / {@code .mcrec.part} file, continued by {@code _sN} files (the 26.3 recorder).</li>
 * </ul>
 */
public final class Recording {
	private static final Pattern SPLIT_SUFFIX = Pattern.compile("^(.*)_s(\\d+)\\.mcrec(\\.part)?$");

	private static final long UNKNOWN = -1;

	private final Path path;
	private final List<SegmentSource> segments;
	private final long durationMillis;

	private Recording(Path path, List<SegmentSource> segments, long durationMillis) {
		this.path = path;
		this.segments = segments;
		this.durationMillis = durationMillis;
	}

	/** One segment and how to open its bytes. */
	public record SegmentSource(String name, Opener opener) {
	}

	@FunctionalInterface
	public interface Opener {
		InputStream open() throws IOException;
	}

	public static Recording open(Path path) throws IOException {
		if (Files.isDirectory(path)) {
			Path session = path.resolve("session.json");
			String json = Files.isRegularFile(session) ? Files.readString(session) : null;
			return new Recording(path, directorySegments(path, json), sessionDurationMillis(json));
		}
		String name = path.getFileName().toString();
		if (name.endsWith(".tar")) {
			TarIndex index = TarIndex.read(path);
			String json = tarSessionJson(index);
			return new Recording(path, tarSegments(index, json), sessionDurationMillis(json));
		}
		if (name.endsWith(".mcrec") || name.endsWith(".mcrec.part")) {
			return new Recording(path, fileChain(path), UNKNOWN);
		}
		throw new IOException("Not a recording: " + path);
	}

	/** True if {@code path} looks like a recording this class can open (used when scanning folders). */
	public static boolean looksLikeRecording(Path path) {
		String name = path.getFileName().toString();
		if (name.endsWith(".tar") || (name.endsWith(".mcrec") || name.endsWith(".mcrec.part")) && !SPLIT_SUFFIX.matcher(name).matches()) {
			return true;
		}
		return Files.isDirectory(path) && Files.isRegularFile(path.resolve("session.json"));
	}

	public Path path() {
		return path;
	}

	/** A stable name for outputs: the file name without recording extensions. */
	public String baseName() {
		String name = path.getFileName().toString();
		for (String ext : List.of(".tar", ".mcrec.part", ".mcrec", ".part")) {
			if (name.endsWith(ext)) {
				return name.substring(0, name.length() - ext.length());
			}
		}
		return name;
	}

	public List<SegmentSource> segments() {
		return segments;
	}

	/** Wall-clock length of the session from its {@code session.json}, if it has one. */
	public OptionalLong durationMillis() {
		return durationMillis >= 0 ? OptionalLong.of(durationMillis) : OptionalLong.empty();
	}

	/** Bytes of all segments on disk (the whole tar for a session tar). */
	public long storedBytes() throws IOException {
		if (!Files.isDirectory(path)) {
			long bytes = Files.size(path);
			if (path.getFileName().toString().endsWith(".tar")) {
				return bytes;
			}
			for (int i = 1; i < segments.size(); i++) {
				bytes += Files.size(path.resolveSibling(segments.get(i).name()));
			}
			return bytes;
		}
		long bytes = 0;
		for (SegmentSource segment : segments) {
			bytes += Files.size(path.resolve(segment.name()));
		}
		return bytes;
	}

	public RecordingReader reader() throws IOException {
		return new RecordingReader(segments);
	}

	private static String tarSessionJson(TarIndex index) throws IOException {
		if (!index.members().containsKey("session.json")) {
			return null;
		}
		try (InputStream in = index.open("session.json")) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	private static List<SegmentSource> tarSegments(TarIndex index, String sessionJson) {
		List<String> names = new ArrayList<>();
		if (sessionJson != null) {
			names.addAll(sessionSegmentNames(sessionJson));
		}
		if (names.isEmpty()) {
			index.members().keySet().stream().filter(n -> n.endsWith(".mcrec")).sorted().forEach(names::add);
		}
		List<SegmentSource> sources = new ArrayList<>();
		for (String name : names) {
			sources.add(new SegmentSource(name, () -> index.open(name)));
		}
		return sources;
	}

	private static List<SegmentSource> directorySegments(Path dir, String sessionJson) throws IOException {
		List<String> names = new ArrayList<>();
		if (sessionJson != null) {
			names.addAll(sessionSegmentNames(sessionJson));
		}
		if (names.isEmpty()) {
			try (Stream<Path> files = Files.list(dir)) {
				files.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".mcrec")).sorted().forEach(names::add);
			}
		}
		return names.stream().map(n -> fileSource(dir.resolve(n))).toList();
	}

	/** A 26.3-recorder file and its size-split continuations ({@code name_s1.mcrec}, ...). */
	private static List<SegmentSource> fileChain(Path first) {
		List<SegmentSource> sources = new ArrayList<>();
		sources.add(fileSource(first));
		String name = first.getFileName().toString();
		String stem = name.substring(0, name.indexOf(".mcrec"));
		for (int i = 1; ; i++) {
			Path next = continuation(first.resolveSibling(stem + "_s" + i));
			if (next == null) {
				return sources;
			}
			sources.add(fileSource(next));
		}
	}

	private static Path continuation(Path stem) {
		for (String ext : List.of(".mcrec", ".mcrec.part")) {
			Path candidate = stem.resolveSibling(stem.getFileName() + ext);
			if (Files.isRegularFile(candidate)) {
				return candidate;
			}
		}
		return null;
	}

	private static SegmentSource fileSource(Path file) {
		return new SegmentSource(file.getFileName().toString(), () -> Files.newInputStream(file));
	}

	/** {@code endedEpochMillis - startedEpochMillis} of a session, or {@link #UNKNOWN}. */
	static long sessionDurationMillis(String sessionJson) {
		if (sessionJson == null) {
			return UNKNOWN;
		}
		JsonObject session = JsonParser.parseString(sessionJson).getAsJsonObject();
		if (!session.has("startedEpochMillis") || !session.has("endedEpochMillis")) {
			return UNKNOWN;
		}
		long millis = session.get("endedEpochMillis").getAsLong() - session.get("startedEpochMillis").getAsLong();
		return millis >= 0 ? millis : UNKNOWN;
	}

	private static List<String> sessionSegmentNames(String sessionJson) {
		List<String> names = new ArrayList<>();
		JsonObject session = JsonParser.parseString(sessionJson).getAsJsonObject();
		if (session.has("segments")) {
			for (JsonElement segment : session.getAsJsonArray("segments")) {
				names.add(segment.getAsJsonObject().get("file").getAsString());
			}
		}
		return names;
	}
}
