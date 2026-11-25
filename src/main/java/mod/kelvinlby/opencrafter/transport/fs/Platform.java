package mod.kelvinlby.opencrafter.transport.fs;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

/** The bits of the host platform that decide where the game and the backend meet. */
public final class Platform {
	private Platform() {
	}

	public static boolean isWindows() {
		return osName().startsWith("windows");
	}

	public static boolean isLinux() {
		return osName().startsWith("linux");
	}

	/** The Flatpak application id when the game runs inside a Flatpak sandbox (e.g. Prism Launcher). */
	public static Optional<String> flatpakId() {
		String id = System.getenv("FLATPAK_ID");
		if (id != null && !id.isBlank()) {
			return Optional.of(id);
		}
		return flatpakInfoName();
	}

	/** {@code name=} of the {@code [Application]} group in {@code /.flatpak-info}. */
	private static Optional<String> flatpakInfoName() {
		Path info = Path.of("/.flatpak-info");
		if (!Files.isReadable(info)) {
			return Optional.empty();
		}
		try (Stream<String> lines = Files.lines(info)) {
			return lines.map(String::strip).filter(line -> line.startsWith("name=")).map(line -> line.substring(5)).findFirst();
		} catch (IOException | UncheckedIOException e) {
			return Optional.empty();
		}
	}

	/** {@code $XDG_RUNTIME_DIR} if set. */
	public static Optional<Path> runtimeDir() {
		String dir = System.getenv("XDG_RUNTIME_DIR");
		return dir == null || dir.isBlank() ? Optional.empty() : Optional.of(Path.of(dir));
	}

	public static Path tempDir() {
		return Path.of(System.getProperty("java.io.tmpdir"));
	}

	private static String osName() {
		return System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
	}
}
