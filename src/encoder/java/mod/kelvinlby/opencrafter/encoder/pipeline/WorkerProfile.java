package mod.kelvinlby.opencrafter.encoder.pipeline;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

/**
 * A worker's game directory, made from the dev client's profile ({@code run/}): its options, mod
 * settings, shader packs and resource packs, so every worker renders exactly as configured there.
 * Large read-only folders are linked, everything the game may write is copied; worlds, logs and
 * crash reports are left out.
 */
final class WorkerProfile {
	private static final Set<String> SKIPPED = Set.of("saves", "logs", "crash-reports", "screenshots", "debug", "downloads", "command_history.txt");
	private static final Set<String> LINKED = Set.of("mods", "shaderpacks", "resourcepacks", "resources");

	private WorkerProfile() {
	}

	/** Makes {@code gameDir} a fresh copy of {@code profile}, so changes to the profile always apply. */
	static void prepare(Path profile, Path gameDir) throws IOException {
		deleteRecursively(gameDir);
		Path temp = gameDir.resolveSibling(gameDir.getFileName() + ".tmp");
		deleteRecursively(temp);
		Files.createDirectories(temp);
		if (Files.isDirectory(profile)) {
			try (Stream<Path> entries = Files.list(profile)) {
				for (Path entry : entries.toList()) {
					place(entry, temp.resolve(entry.getFileName().toString()));
				}
			}
		}
		forceWindowed(temp.resolve("options.txt"));
		Files.move(temp, gameDir);
	}

	/**
	 * The window is created before any mod code runs, from {@code options.txt}: fullscreen would size
	 * the framebuffer to the display mode instead of the capture size.
	 */
	private static void forceWindowed(Path options) throws IOException {
		if (Files.isRegularFile(options)) {
			List<String> lines = Files.readAllLines(options).stream()
				.map(line -> line.startsWith("fullscreen:") ? "fullscreen:false" : line)
				.toList();
			Files.write(options, lines);
		}
	}

	private static void place(Path entry, Path target) throws IOException {
		String name = entry.getFileName().toString();
		if (SKIPPED.contains(name)) {
			return;
		}
		if (LINKED.contains(name)) {
			Files.createSymbolicLink(target, entry.toAbsolutePath());
		} else {
			copyRecursively(entry, target);
		}
	}

	private static void copyRecursively(Path source, Path target) throws IOException {
		if (!Files.isDirectory(source)) {
			Files.copy(source, target, StandardCopyOption.COPY_ATTRIBUTES);
			return;
		}
		Files.walkFileTree(source, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
				Files.createDirectories(target.resolve(source.relativize(dir).toString()));
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.copy(file, target.resolve(source.relativize(file).toString()), StandardCopyOption.COPY_ATTRIBUTES);
				return FileVisitResult.CONTINUE;
			}
		});
	}

	static void deleteRecursively(Path path) throws IOException {
		if (!Files.exists(path, java.nio.file.LinkOption.NOFOLLOW_LINKS)) {
			return;
		}
		Files.walkFileTree(path, new SimpleFileVisitor<>() {
			@Override
			public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
				Files.delete(file);
				return FileVisitResult.CONTINUE;
			}

			@Override
			public FileVisitResult postVisitDirectory(Path dir, IOException e) throws IOException {
				Files.delete(dir);
				return FileVisitResult.CONTINUE;
			}
		});
	}
}
