package mod.kelvinlby.crafter.engine;

import com.google.gson.Gson;
import com.google.gson.annotations.SerializedName;
import mod.kelvinlby.crafter.OpenCrafter;
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.xz.XZCompressorInputStream;

import java.io.*;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;

public class EngineDownloader {
    private static final String ENGINE_REPO = "Kelvinlby/open-crafter-engine";
    private static final String ENGINE_FILENAME = "open-crafter-engine.tar.xz";
    private static final String METADATA_FILE = "engine-info.json";
    private static final String DOWNLOAD_URL =
        "https://github.com/" + ENGINE_REPO + "/releases/latest/download/" + ENGINE_FILENAME;
    private static final String LATEST_RELEASE_API =
        "https://api.github.com/repos/" + ENGINE_REPO + "/releases/latest";

    private static final HttpClient HTTP_CLIENT = HttpClient.newHttpClient();
    private static final Gson GSON = new Gson();

    /**
     * Checks if the engine needs to be downloaded or updated.
     * Downloads/updates if necessary.
     * Uses atomic replacement to avoid corruption on failure.
     *
     * @param openCrafterDir The open-crafter folder (e.g. .minecraft/open-crafter)
     * @param engineDir The engine folder inside open-crafter (e.g. .minecraft/open-crafter/engine)
     */
    public static void ensureEngineUpToDate(Path openCrafterDir, Path engineDir) throws IOException, InterruptedException {
        Files.createDirectories(engineDir);

        // Metadata file is in open-crafter/, not in engine/
        Path metadataPath = openCrafterDir.resolve(METADATA_FILE);
        EngineMetadata currentMetadata = loadMetadata(metadataPath);

        ReleaseInfo latestRelease = fetchLatestReleaseInfo();

        if (currentMetadata == null || !currentMetadata.matches(latestRelease)) {
            OpenCrafter.LOGGER.info("Engine update available: {} -> {}",
                currentMetadata != null ? currentMetadata.version : "none",
                latestRelease.version);

            // Atomic replacement: extract to temp dir first, then swap
            Path tempDir = Files.createTempDirectory("open-crafter-engine-new");
            Path backupDir = null;
            boolean success = false;

            try {
                downloadAndExtract(tempDir, latestRelease);

                // Backup old engine if it exists
                if (currentMetadata != null) {
                    backupDir = Files.createTempDirectory("open-crafter-engine-old");
                    moveDirectory(engineDir, backupDir);
                }

                // Move new engine into place
                moveDirectory(tempDir, engineDir);
                saveMetadata(metadataPath, latestRelease);

                success = true;
                OpenCrafter.LOGGER.info("Engine update complete");

                // Clean up backup on success
                if (backupDir != null) {
                    deleteRecursive(backupDir);
                }
            } catch (Exception e) {
                OpenCrafter.LOGGER.error("Engine update failed", e);

                // Restore from backup if update failed
                if (Files.exists(engineDir)) {
                    deleteRecursive(engineDir);
                }
                // Backup might be null if this was a fresh install
                if (backupDir != null && Files.exists(backupDir)) {
                    moveDirectory(backupDir, engineDir);
                    OpenCrafter.LOGGER.info("Restored previous engine version");
                }

                throw e;
            } finally {
                // Clean up temp directories
                if (Files.exists(tempDir)) {
                    deleteRecursive(tempDir);
                }
            }
        } else {
            OpenCrafter.LOGGER.info("Inference engine is up to date (version {})", currentMetadata.version);
        }
    }

    private static EngineMetadata loadMetadata(Path metadataPath) {
        if (!Files.exists(metadataPath)) {
            return null;
        }
        try {
            String json = Files.readString(metadataPath);
            return GSON.fromJson(json, EngineMetadata.class);
        } catch (IOException e) {
            OpenCrafter.LOGGER.warn("Failed to load engine metadata", e);
            return null;
        }
    }

    private static void saveMetadata(Path metadataPath, ReleaseInfo release) throws IOException {
        EngineMetadata metadata = new EngineMetadata(release.version, release.downloadHash);
        String json = GSON.toJson(metadata);
        Files.writeString(metadataPath, json);
    }

    private static ReleaseInfo fetchLatestReleaseInfo() throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(java.net.URI.create(LATEST_RELEASE_API))
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "open-crafter")
            .GET()
            .build();

        HttpResponse<String> response = HTTP_CLIENT.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new IOException("Failed to fetch latest release: HTTP " + response.statusCode());
        }

        GitHubRelease release = GSON.fromJson(response.body(), GitHubRelease.class);

        // Find the asset matching our filename
        for (GitHubAsset asset : release.assets) {
            if (ENGINE_FILENAME.equals(asset.name)) {
                return new ReleaseInfo(release.tagName, asset.downloadUrl, String.valueOf(asset.size));
            }
        }

        throw new IOException("Asset not found: " + ENGINE_FILENAME);
    }

    private static void downloadAndExtract(Path targetDir, ReleaseInfo release) throws IOException, InterruptedException {
        Path tempFile = Files.createTempFile("open-crafter-engine", ".tar.xz");

        try {
            downloadEngine(tempFile, release.downloadUrl);
            extractArchive(tempFile, targetDir);
        } finally {
            Files.deleteIfExists(tempFile);
        }
    }

    private static void downloadEngine(Path destPath, String url) throws IOException, InterruptedException {
        OpenCrafter.LOGGER.info("Downloading inference engine from: {}", url);

        HttpRequest request = HttpRequest.newBuilder()
            .uri(java.net.URI.create(url))
            .header("Accept", "application/octet-stream")
            .GET()
            .build();

        long startTime = System.currentTimeMillis();

        HttpResponse<Path> response = HTTP_CLIENT.send(
            request,
            HttpResponse.BodyHandlers.ofFile(destPath)
        );

        int statusCode = response.statusCode();
        if (statusCode != 200) {
            throw new IOException("Failed to download engine: HTTP " + statusCode);
        }

        long fileSize = Files.size(destPath);
        long duration = System.currentTimeMillis() - startTime;

        OpenCrafter.LOGGER.info("Download complete: {} MB in {} ms",
            String.format("%.2f", fileSize / (1024.0 * 1024.0)), duration);
    }

    private static void extractArchive(Path archivePath, Path targetDir) throws IOException {
        OpenCrafter.LOGGER.info("Extracting inference engine...");

        try (FileInputStream fis = new FileInputStream(archivePath.toFile());
             XZCompressorInputStream xzis = new XZCompressorInputStream(fis);
             TarArchiveInputStream taris = new TarArchiveInputStream(xzis)) {

            TarArchiveEntry entry;
            while ((entry = taris.getNextTarEntry()) != null) {
                Path entryPath = targetDir.resolve(entry.getName());

                if (entry.isDirectory()) {
                    Files.createDirectories(entryPath);
                } else {
                    Files.createDirectories(entryPath.getParent());
                    Files.copy(taris, entryPath, StandardCopyOption.REPLACE_EXISTING);

                    // Preserve executable permissions
                    entryPath.toFile().setExecutable(true, false);
                }
            }
        }

        OpenCrafter.LOGGER.info("Extraction complete");
    }

    /**
     * Moves a directory by copying + deleting (works across filesystems).
     */
    private static void moveDirectory(Path source, Path target) throws IOException {
        if (Files.exists(target)) {
            deleteRecursive(target);
        }
        Files.createDirectories(target.getParent());

        // Copy first (safer than rename across filesystems)
        copyDirectory(source, target);
        // Then delete source
        deleteRecursive(source);
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        Files.walkFileTree(source, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Path targetDir = target.resolve(source.relativize(dir));
                Files.createDirectories(targetDir);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path targetFile = target.resolve(source.relativize(file));
                Files.copy(file, targetFile, StandardCopyOption.REPLACE_EXISTING);
                // Preserve executable permission
                if (Files.isExecutable(file)) {
                    targetFile.toFile().setExecutable(true, false);
                }
                return FileVisitResult.CONTINUE;
            }
        });
    }

    private static void deleteRecursive(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        Files.walkFileTree(path, new SimpleFileVisitor<Path>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.delete(dir);
                return FileVisitResult.CONTINUE;
            }
        });
    }

    // === Data Classes ===

    private static class EngineMetadata {
        String version;
        String hash;

        EngineMetadata() {}

        EngineMetadata(String version, String hash) {
            this.version = version;
            this.hash = hash;
        }

        boolean matches(ReleaseInfo release) {
            return version != null && version.equals(release.version);
        }
    }

    private static class ReleaseInfo {
        final String version;
        final String downloadUrl;
        final String downloadHash;

        ReleaseInfo(String version, String downloadUrl, String downloadHash) {
            this.version = version;
            this.downloadUrl = downloadUrl;
            this.downloadHash = downloadHash;
        }
    }

    private static class GitHubRelease {
        @SerializedName("tag_name")
        String tagName;

        GitHubAsset[] assets;
    }

    private static class GitHubAsset {
        String name;

        @SerializedName("browser_download_url")
        String downloadUrl;

        long size;
    }
}
