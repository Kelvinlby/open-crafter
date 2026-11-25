package mod.kelvinlby.opencrafter.transport.discovery;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import mod.kelvinlby.opencrafter.transport.fs.PrivateFiles;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.channels.FileChannel;
import java.nio.channels.FileLock;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.util.Map;

/**
 * This game's {@code <instance>.json} in the discovery directory, telling backends how to
 * connect. A lock on {@code <instance>.lock} is held for the game's lifetime so that other
 * games (and backends) can tell live entries from ones left behind by a crash.
 */
public final class DiscoveryEntry implements AutoCloseable {
	static final String JSON = ".json";
	static final String LOCK = ".lock";
	private static final Logger LOGGER = LoggerFactory.getLogger("open-crafter/transport");
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().serializeNulls().create();

	private final Path file;
	private final Path lockFile;
	private final FileChannel lockChannel;
	private final FileLock lock;

	private DiscoveryEntry(Path file, Path lockFile, FileChannel lockChannel, FileLock lock) {
		this.file = file;
		this.lockFile = lockFile;
		this.lockChannel = lockChannel;
		this.lock = lock;
	}

	/** Locks {@code <instance>.lock}, then atomically writes {@code <instance>.json} with {@code info}. */
	public static DiscoveryEntry publish(Path dir, String instance, Map<String, Object> info) throws IOException {
		PrivateFiles.createPrivateDirectory(dir);
		Path lockFile = dir.resolve(instance + LOCK);
		FileChannel channel = FileChannel.open(lockFile, StandardOpenOption.CREATE, StandardOpenOption.WRITE);
		try {
			FileLock lock = channel.lock();
			Path file = dir.resolve(instance + JSON);
			writeAtomically(file, GSON.toJson(info));
			return new DiscoveryEntry(file, lockFile, channel, lock);
		} catch (IOException | RuntimeException e) {
			channel.close();
			Files.deleteIfExists(lockFile);
			throw e;
		}
	}

	public Path file() {
		return file;
	}

	@Override
	public void close() {
		deleteQuietly(file);
		try {
			lock.release();
			lockChannel.close();
		} catch (IOException e) {
			LOGGER.debug("Could not release discovery lock {}", lockFile, e);
		}
		deleteQuietly(lockFile);
	}

	private static void writeAtomically(Path file, String json) throws IOException {
		Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
		Files.writeString(tmp, json, StandardCharsets.UTF_8);
		PrivateFiles.restrictToOwner(tmp);
		Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
	}

	private static void deleteQuietly(Path path) {
		try {
			Files.deleteIfExists(path);
		} catch (IOException e) {
			LOGGER.debug("Could not delete {}", path, e);
		}
	}
}
