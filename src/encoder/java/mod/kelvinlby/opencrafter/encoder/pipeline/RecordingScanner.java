package mod.kelvinlby.opencrafter.encoder.pipeline;

import mod.kelvinlby.opencrafter.encoder.format.Recording;

import java.io.IOException;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.List;

/** Finds every recording under the given paths (a session folder counts as one recording). */
final class RecordingScanner {
	private RecordingScanner() {
	}

	static List<Path> find(List<Path> inputs) throws IOException {
		List<Path> found = new ArrayList<>();
		for (Path input : inputs) {
			if (!Files.isDirectory(input) || Recording.looksLikeRecording(input)) {
				found.add(input);
				continue;
			}
			Files.walkFileTree(input, new SimpleFileVisitor<>() {
				@Override
				public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) {
					if (!dir.equals(input) && Recording.looksLikeRecording(dir)) {
						found.add(dir);
						return FileVisitResult.SKIP_SUBTREE;
					}
					return FileVisitResult.CONTINUE;
				}

				@Override
				public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) {
					if (Recording.looksLikeRecording(file)) {
						found.add(file);
					}
					return FileVisitResult.CONTINUE;
				}
			});
		}
		return found.stream().distinct().sorted().toList();
	}
}
