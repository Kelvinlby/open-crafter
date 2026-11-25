package mod.kelvinlby.opencrafter.transport.fs;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Platform defaults for where the game and the backend meet.
 *
 * <p>The <b>discovery directory</b> holds one {@code <instance>.json} per running game plus,
 * by default, its socket and (off Linux) its frame ring. Backends find games by listing it:
 * <ul>
 *   <li>Linux: {@code $XDG_RUNTIME_DIR/open-crafter}</li>
 *   <li>Linux in a Flatpak: {@code $XDG_RUNTIME_DIR/app/<flatpak id>/open-crafter}, the one
 *       runtime folder a sandbox shares with the host, at the same path on both sides</li>
 *   <li>macOS, Windows, Linux without {@code XDG_RUNTIME_DIR}: {@code <temp dir>/open-crafter}
 *       (per-user {@code $TMPDIR} / {@code %TEMP%})</li>
 * </ul>
 */
public final class TransportPaths {
	public static final String DIRECTORY_NAME = "open-crafter";
	public static final String RING_PREFIX = "open-crafter-";

	private static final Pattern RING_NAME = Pattern.compile(Pattern.quote(RING_PREFIX) + "(\\d+)(-[0-9a-f]+)?");
	private static final Path DEV_SHM = Path.of("/dev/shm");

	private TransportPaths() {
	}

	public static Path discoveryDirectory() {
		return discoveryBase().resolve(DIRECTORY_NAME);
	}

	/** {@code configured} if set, else {@code <discovery dir>/<instance>.sock}. */
	public static Path socket(String configured, Path discoveryDir, String instance) {
		return isSet(configured) ? Path.of(configured) : discoveryDir.resolve(instance + ".sock");
	}

	/** {@code configured} if set, else {@code /dev/shm/open-crafter-<instance>} on Linux, else the discovery dir. */
	public static Path ring(String configured, Path discoveryDir, String instance) {
		if (isSet(configured)) {
			return Path.of(configured);
		}
		return usesDevShm() ? DEV_SHM.resolve(RING_PREFIX + instance) : discoveryDir.resolve(instance + ".ring");
	}

	/** True when {@code configured} is empty, i.e. the platform default applies. */
	public static boolean isDefault(String configured) {
		return !isSet(configured);
	}

	/** Deletes {@code /dev/shm} rings of processes that no longer exist (left by crashes). */
	public static void deleteStaleDevShmRings() throws IOException {
		if (!usesDevShm()) {
			return;
		}
		try (DirectoryStream<Path> files = Files.newDirectoryStream(DEV_SHM, RING_PREFIX + "*")) {
			for (Path file : files) {
				deleteIfOrphaned(file);
			}
		}
	}

	/** {@code /dev/shm} is private to a Flatpak sandbox, so it is only used outside one. */
	private static boolean usesDevShm() {
		return Files.isDirectory(DEV_SHM) && Platform.flatpakId().isEmpty();
	}

	private static Path discoveryBase() {
		return Platform.flatpakId()
			.flatMap(id -> Platform.runtimeDir().map(dir -> dir.resolve("app").resolve(id)))
			.or(Platform::runtimeDir)
			.orElseGet(Platform::tempDir);
	}

	private static void deleteIfOrphaned(Path file) {
		Matcher matcher = RING_NAME.matcher(file.getFileName().toString());
		if (!matcher.matches() || isAlive(Long.parseLong(matcher.group(1)))) {
			return;
		}
		try {
			Files.deleteIfExists(file);
		} catch (IOException ignored) {
			// Someone else's file: not ours to clean.
		}
	}

	private static boolean isAlive(long pid) {
		return ProcessHandle.of(pid).map(ProcessHandle::isAlive).orElse(false);
	}

	private static boolean isSet(String value) {
		return value != null && !value.isBlank();
	}
}
