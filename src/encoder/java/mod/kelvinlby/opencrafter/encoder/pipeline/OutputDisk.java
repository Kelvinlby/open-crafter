package mod.kelvinlby.opencrafter.encoder.pipeline;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

/** The file system the episodes are written to. */
final class OutputDisk implements JobScheduler.Disk {
	private final FileStore store;

	OutputDisk(Path output) throws IOException {
		this.store = Files.getFileStore(output);
	}

	@Override
	public long freeBytes() {
		try {
			return store.getUsableSpace();
		} catch (IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	@Override
	public long bytesIn(Path episode) {
		return sizeOf(episode);
	}

	/** Bytes of the regular files directly in {@code dir} (episodes are flat); 0 if it does not exist. */
	static long sizeOf(Path dir) {
		if (!Files.isDirectory(dir)) {
			return 0;
		}
		try (Stream<Path> files = Files.list(dir)) {
			return files.mapToLong(f -> f.toFile().length()).sum();
		} catch (IOException e) {
			return 0;
		}
	}
}
