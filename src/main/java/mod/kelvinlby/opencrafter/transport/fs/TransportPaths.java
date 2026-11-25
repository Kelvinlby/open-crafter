package mod.kelvinlby.opencrafter.transport.fs;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Platform defaults for the socket and shared-memory paths. */
public final class TransportPaths {
	public static final String SOCKET_NAME = "open-crafter.sock";
	public static final String RING_PREFIX = "open-crafter-";

	private static final Pattern RING_NAME = Pattern.compile(Pattern.quote(RING_PREFIX) + "(\\d+)");
	private static final Path DEV_SHM = Path.of("/dev/shm");

	private TransportPaths() {
	}

	/** {@code configured} if set, else {@code $XDG_RUNTIME_DIR/open-crafter.sock}, else the temp dir. */
	public static Path socket(String configured) {
		if (isSet(configured)) {
			return Path.of(configured);
		}
		String runtimeDir = System.getenv("XDG_RUNTIME_DIR");
		Path dir = isSet(runtimeDir) ? Path.of(runtimeDir) : tempDir();
		return dir.resolve(SOCKET_NAME);
	}

	/** {@code configured} if set, else {@code /dev/shm/open-crafter-<pid>} or the temp dir. */
	public static Path ring(String configured) {
		if (isSet(configured)) {
			return Path.of(configured);
		}
		return ringDirectory().resolve(RING_PREFIX + ProcessHandle.current().pid());
	}

	/** True when {@code configured} is empty, i.e. the platform default applies. */
	public static boolean isDefault(String configured) {
		return !isSet(configured);
	}

	/** Deletes default-named rings of processes that no longer exist (left by crashes). */
	public static void deleteStaleRings() throws IOException {
		try (DirectoryStream<Path> files = Files.newDirectoryStream(ringDirectory(), RING_PREFIX + "*")) {
			for (Path file : files) {
				deleteIfOrphaned(file);
			}
		}
	}

	private static void deleteIfOrphaned(Path file) {
		Matcher matcher = RING_NAME.matcher(file.getFileName().toString());
		if (!matcher.matches() || isAlive(Long.parseLong(matcher.group(1)))) {
			return;
		}
		try {
			Files.deleteIfExists(file);
		} catch (IOException ignored) {
			// Someone else's file (shared temp dir): not ours to clean.
		}
	}

	private static boolean isAlive(long pid) {
		return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
	}

	private static Path ringDirectory() {
		return Files.isDirectory(DEV_SHM) ? DEV_SHM : tempDir();
	}

	private static Path tempDir() {
		return Path.of(System.getProperty("java.io.tmpdir"));
	}

	private static boolean isSet(String value) {
		return value != null && !value.isBlank();
	}
}
