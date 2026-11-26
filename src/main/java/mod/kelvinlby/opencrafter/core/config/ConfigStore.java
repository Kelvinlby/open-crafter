package mod.kelvinlby.opencrafter.core.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/**
 * Loads, holds and atomically persists {@link OpenCrafterConfig} as JSON. Editors work on a
 * {@link #copy()} and hand it back through {@link #replace}, which tells the change listeners.
 */
public final class ConfigStore {
	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

	private final Path file;
	private final List<BiConsumer<OpenCrafterConfig, OpenCrafterConfig>> listeners = new CopyOnWriteArrayList<>();
	private volatile OpenCrafterConfig current;

	private ConfigStore(Path file, OpenCrafterConfig initial) {
		this.file = file;
		this.current = initial;
	}

	public static ConfigStore load(Path file) {
		ConfigStore store = new ConfigStore(file, readOrDefault(file));
		store.save();
		return store;
	}

	public OpenCrafterConfig get() {
		return current;
	}

	/** Applies a change and persists it. Saving happens on the calling thread; keep it off hot paths. */
	public synchronized void update(Consumer<OpenCrafterConfig> change) {
		change.accept(current);
		save();
	}

	/** A deep, independent copy of the current settings to edit. */
	public OpenCrafterConfig copy() {
		return copyOf(current);
	}

	public static OpenCrafterConfig copyOf(OpenCrafterConfig config) {
		return GSON.fromJson(GSON.toJsonTree(config), OpenCrafterConfig.class);
	}

	/** Swaps in edited settings, persists them, then calls every listener with (old, new). */
	public void replace(OpenCrafterConfig edited) {
		OpenCrafterConfig old;
		synchronized (this) {
			old = current;
			current = edited;
			save();
		}
		listeners.forEach(listener -> listener.accept(old, edited));
	}

	/** Called after {@link #replace}, on the replacing thread, with the old and the new settings. */
	public void onReplace(BiConsumer<OpenCrafterConfig, OpenCrafterConfig> listener) {
		listeners.add(listener);
	}

	private synchronized void save() {
		try {
			Files.createDirectories(file.getParent());
			Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
			try (Writer writer = Files.newBufferedWriter(tmp)) {
				GSON.toJson(current, writer);
			}
			Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
		} catch (IOException e) {
			throw new IllegalStateException("Cannot save config " + file, e);
		}
	}

	private static OpenCrafterConfig readOrDefault(Path file) {
		if (!Files.exists(file)) {
			return new OpenCrafterConfig();
		}
		try (Reader reader = Files.newBufferedReader(file)) {
			OpenCrafterConfig config = GSON.fromJson(reader, OpenCrafterConfig.class);
			return config == null ? new OpenCrafterConfig() : config;
		} catch (IOException | JsonParseException e) {
			return new OpenCrafterConfig();
		}
	}
}
