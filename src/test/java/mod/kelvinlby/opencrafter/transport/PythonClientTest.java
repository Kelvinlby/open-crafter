package mod.kelvinlby.opencrafter.transport;

import mod.kelvinlby.opencrafter.api.Args;
import mod.kelvinlby.opencrafter.api.ThreadAffinity;
import mod.kelvinlby.opencrafter.api.frame.DType;
import mod.kelvinlby.opencrafter.api.frame.FrameSlot;
import mod.kelvinlby.opencrafter.api.frame.StreamSpec;
import mod.kelvinlby.opencrafter.api.handler.HandlerRegistry;
import mod.kelvinlby.opencrafter.core.config.TransportConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Runs {@code python/tests/e2e_against_java.py} against a live transport. Skipped unless a
 * Python with msgpack and numpy is found: {@code -Dopencrafter.python=...},
 * {@code $OPENCRAFTER_PYTHON}, or {@code python/.venv/bin/python}.
 */
class PythonClientTest {
	@TempDir
	Path dir;
	private final ExecutorService mainThread = Executors.newSingleThreadExecutor(r -> new Thread(r, "fake-main"));
	private final HandlerRegistry handlers = new HandlerRegistry();
	private final AtomicLong counter = new AtomicLong();
	private TransportModule transport;

	@BeforeEach
	void start() {
		TransportConfig config = new TransportConfig();
		config.socketPath = dir.resolve("py.sock").toString();
		config.sharedMemoryPath = dir.resolve("py-ring").toString();
		transport = TransportModule.start(config, handlers, mainThread);
		handlers.register("echo", ThreadAffinity.ANY, Args::raw);
		handlers.register("main.thread", ThreadAffinity.MAIN, args -> Thread.currentThread().getName());
		handlers.register("boom", ThreadAffinity.ANY, args -> {
			throw new IllegalArgumentException("kaboom");
		});
		handlers.register("test.counter", ThreadAffinity.MAIN, args -> counter.incrementAndGet());
		handlers.register("test.event", ThreadAffinity.ANY, args -> {
			transport.events().publish("ping", args.raw());
			return null;
		});
		handlers.register("test.frame", ThreadAffinity.MAIN, this::publishTestFrame);
	}

	@AfterEach
	void stop() {
		transport.close();
		mainThread.shutdownNow();
	}

	@Test
	void pythonClientPassesEndToEndChecks() throws Exception {
		Path projectDir = Path.of(System.getProperty("opencrafter.projectDir", ".")).toAbsolutePath();
		Path python = findPython(projectDir);
		assumeTrue(python != null && hasDependencies(python), "no Python with msgpack+numpy available");

		ProcessBuilder builder = new ProcessBuilder(python.toString(), "tests/e2e_against_java.py", transport.socketPath().toString())
			.directory(projectDir.resolve("python").toFile())
			.redirectErrorStream(true);
		builder.environment().put("PYTHONPATH", projectDir.resolve("python").toString());
		Process process = builder.start();
		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		assertTrue(process.waitFor(60, TimeUnit.SECONDS), "python timed out");
		assertEquals(0, process.exitValue(), output);
		assertTrue(output.contains("background=False: OK"), output);
	}

	/** rgb = (x + y*w + index) % 251 (+channel), alpha 255; flow = (x, -y); one occluded pixel per row. */
	private Object publishTestFrame(Args args) {
		int width = args.integer("width");
		int height = args.integer("height");
		int index = args.integer("index");
		FrameSlot slot = transport.frames().acquire(List.of(
			new StreamSpec("rgb", DType.UINT8, 4, width, height),
			new StreamSpec("flow", DType.FLOAT32, 2, width, height),
			new StreamSpec("flow_occluded", DType.UINT8, 1, width, height)));
		if (slot == null) {
			throw new IllegalStateException("no free slot");
		}
		fillRgb(slot.stream("rgb"), width, height, index);
		fillFlow(slot.stream("flow"), width, height);
		fillOccluded(slot.stream("flow_occluded"), width, height);
		slot.setMeta(Map.of("index", index, "size", List.of(width, height)));
		slot.commit(index, System.nanoTime());
		return null;
	}

	private static void fillRgb(ByteBuffer rgb, int width, int height, int index) {
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				int base = (x + y * width + index) % 251;
				rgb.put((byte) base).put((byte) (base + 1)).put((byte) (base + 2)).put((byte) 255);
			}
		}
	}

	private static void fillFlow(ByteBuffer flow, int width, int height) {
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				flow.putFloat(x).putFloat(-y);
			}
		}
	}

	private static void fillOccluded(ByteBuffer occluded, int width, int height) {
		for (int y = 0; y < height; y++) {
			for (int x = 0; x < width; x++) {
				occluded.put((byte) (x == 0 ? 1 : 0));
			}
		}
	}

	private static Path findPython(Path projectDir) {
		String configured = System.getProperty("opencrafter.python", System.getenv("OPENCRAFTER_PYTHON"));
		if (configured != null && !configured.isBlank()) {
			return Path.of(configured);
		}
		Path venv = projectDir.resolve("python/.venv/bin/python");
		return Files.isExecutable(venv) ? venv : null;
	}

	private static boolean hasDependencies(Path python) throws IOException, InterruptedException {
		Process probe = new ProcessBuilder(python.toString(), "-c", "import msgpack, numpy").redirectErrorStream(true).start();
		probe.getInputStream().readAllBytes();
		return probe.waitFor(30, TimeUnit.SECONDS) && probe.exitValue() == 0;
	}
}
