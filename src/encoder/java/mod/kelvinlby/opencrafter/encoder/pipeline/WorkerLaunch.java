package mod.kelvinlby.opencrafter.encoder.pipeline;

import com.google.gson.Gson;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * How to start a worker client: the Loom dev launch of the mod plus the encoder mod, as written
 * by the {@code encoderWorkerLaunch} Gradle task.
 */
record WorkerLaunch(String java, List<String> jvmArgs, List<String> classpath, String mainClass, List<String> args, String profileDir) {
	private static final Gson GSON = new Gson();

	static WorkerLaunch read(Path spec) throws IOException {
		return GSON.fromJson(Files.readString(spec), WorkerLaunch.class);
	}

	/** The process for one job, in its own game directory, rendering offscreen or into Xvfb. */
	ProcessBuilder process(Path gameDir, Path jobFile, EncodeConfig config, EncodeOptions.Display display, Path log) {
		List<String> command = new ArrayList<>();
		if (display == EncodeOptions.Display.XVFB) {
			command.addAll(List.of("xvfb-run", "-a", "-s", "-screen 0 " + config.width + "x" + config.height + "x24"));
		}
		command.add(java);
		command.addAll(jvmArgs);
		command.add("-D" + Job.PROPERTY + "=" + jobFile.toAbsolutePath());
		command.add("-cp");
		command.add(String.join(File.pathSeparator, classpath));
		command.add(mainClass);
		command.addAll(args);
		command.addAll(List.of("--width", Integer.toString(config.width), "--height", Integer.toString(config.height)));
		ProcessBuilder builder = new ProcessBuilder(command).directory(gameDir.toFile());
		builder.redirectErrorStream(true).redirectOutput(ProcessBuilder.Redirect.appendTo(log.toFile()));
		configureDisplay(builder.environment(), display);
		return builder;
	}

	private static void configureDisplay(Map<String, String> env, EncodeOptions.Display display) {
		switch (display) {
			case OFFSCREEN -> env.put("SDL_VIDEO_DRIVER", "offscreen");
			case XVFB -> {
				env.remove("WAYLAND_DISPLAY");
				env.put("SDL_VIDEO_DRIVER", "x11");
			}
			case NATIVE -> {
			}
		}
	}
}
