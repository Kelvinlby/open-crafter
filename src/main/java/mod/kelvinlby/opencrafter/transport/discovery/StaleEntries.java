package mod.kelvinlby.opencrafter.transport.discovery;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.channels.OverlappingFileLockException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Removes discovery entries (and their default socket / ring files) whose game is gone. */
public final class StaleEntries {
	private static final List<String> INSTANCE_FILES = List.of(DiscoveryEntry.JSON, ".sock", ".ring", ".json.tmp");

	private StaleEntries() {
	}

	public static void clean(Path dir) throws IOException {
		if (!Files.isDirectory(dir)) {
			return;
		}
		for (String instance : instances(dir)) {
			if (!isLive(dir.resolve(instance + DiscoveryEntry.LOCK))) {
				delete(dir, instance);
			}
		}
	}

	private static Set<String> instances(Path dir) throws IOException {
		Set<String> instances = new HashSet<>();
		try (DirectoryStream<Path> files = Files.newDirectoryStream(dir)) {
			for (Path file : files) {
				String name = file.getFileName().toString();
				int dot = name.indexOf('.');
				if (dot > 0) {
					instances.add(name.substring(0, dot));
				}
			}
		}
		return instances;
	}

	/** A game holds its lock file locked; one we can lock (or that is missing) is stale. */
	private static boolean isLive(Path lockFile) {
		if (!Files.exists(lockFile)) {
			return false;
		}
		try (FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.WRITE)) {
			FileLock lock = channel.tryLock();
			if (lock == null) {
				return true;
			}
			lock.release();
			return false;
		} catch (OverlappingFileLockException e) {
			return true;
		} catch (IOException e) {
			return true;
		}
	}

	private static void delete(Path dir, String instance) {
		for (String suffix : INSTANCE_FILES) {
			deleteQuietly(dir.resolve(instance + suffix));
		}
		deleteQuietly(dir.resolve(instance + DiscoveryEntry.LOCK));
	}

	private static void deleteQuietly(Path path) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException ignored) {
			// In use or not ours: leave it.
		}
	}
}
