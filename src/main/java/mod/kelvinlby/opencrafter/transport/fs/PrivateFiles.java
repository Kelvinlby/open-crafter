package mod.kelvinlby.opencrafter.transport.fs;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Set;

/** Owner-only file helpers: the socket and frame ring give full control of the player. */
public final class PrivateFiles {
	private static final Set<PosixFilePermission> OWNER_READ_WRITE =
		EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

	private static final Set<PosixFilePermission> OWNER_ALL =
		EnumSet.of(PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE, PosixFilePermission.OWNER_EXECUTE);

	private PrivateFiles() {
	}

	/** Restricts {@code path} to rw------- where POSIX permissions exist; no-op elsewhere. */
	public static void restrictToOwner(Path path) throws IOException {
		try {
			Files.setPosixFilePermissions(path, OWNER_READ_WRITE);
		} catch (UnsupportedOperationException ignored) {
			// Non-POSIX file system (Windows): rely on the user profile's ACLs.
		}
	}

	/** Creates {@code dir} (and parents) and restricts it to rwx------ where POSIX permissions exist. */
	public static void createPrivateDirectory(Path dir) throws IOException {
		Files.createDirectories(dir);
		try {
			Files.setPosixFilePermissions(dir, OWNER_ALL);
		} catch (UnsupportedOperationException ignored) {
			// Windows: the user's temp folder is already private.
		}
	}

	/** Deletes a leftover file and makes sure the parent directory exists. */
	public static void prepareFresh(Path path) throws IOException {
		Files.deleteIfExists(path);
		Path parent = path.toAbsolutePath().getParent();
		if (parent != null) {
			Files.createDirectories(parent);
		}
	}
}
