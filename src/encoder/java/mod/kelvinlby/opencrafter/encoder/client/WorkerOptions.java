package mod.kelvinlby.opencrafter.encoder.client;

import com.mojang.blaze3d.platform.Window;
import mod.kelvinlby.opencrafter.encoder.EncoderLog;
import mod.kelvinlby.opencrafter.encoder.pipeline.EncodeConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.sounds.SoundSource;

/**
 * Settings a worker needs whatever the copied profile says. Only things that do not change the
 * picture (frame pacing, focus, sound) plus the capture size; never saved back to {@code options.txt}.
 */
final class WorkerOptions {
	private static final int UNLIMITED_FPS = 260;

	private WorkerOptions() {
	}

	static void apply(Minecraft minecraft, EncodeConfig config) {
		Options options = minecraft.options;
		options.enableVsync().set(false);
		options.framerateLimit().set(UNLIMITED_FPS);
		options.pauseOnLostFocus = false;
		options.getSoundSourceOptionInstance(SoundSource.MASTER).set(0.0);
		ensureWindowSize(minecraft.getWindow(), config);
	}

	private static void ensureWindowSize(Window window, EncodeConfig config) {
		if (window.getWidth() != config.width || window.getHeight() != config.height) {
			EncoderLog.LOGGER.warn("Framebuffer is {}x{}, not the configured {}x{}", window.getWidth(), window.getHeight(), config.width, config.height);
		}
	}
}
