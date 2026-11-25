package mod.kelvinlby.opencrafter.transport;

import mod.kelvinlby.opencrafter.api.BackendLifecycle;
import mod.kelvinlby.opencrafter.api.ThreadAffinity;
import mod.kelvinlby.opencrafter.api.frame.DType;
import mod.kelvinlby.opencrafter.api.frame.FrameSlot;
import mod.kelvinlby.opencrafter.api.frame.StreamSpec;
import mod.kelvinlby.opencrafter.api.handler.HandlerRegistry;
import mod.kelvinlby.opencrafter.core.config.TransportConfig;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(30)
class TransportEndToEndTest {
	@TempDir
	Path dir;
	private final ExecutorService mainThread = Executors.newSingleThreadExecutor(r -> new Thread(r, "fake-main"));
	private final HandlerRegistry handlers = new HandlerRegistry();
	private TransportModule transport;
	private TransportConfig config;

	@BeforeEach
	void start() {
		config = new TransportConfig();
		config.socketPath = dir.resolve("oc.sock").toString();
		config.sharedMemoryPath = dir.resolve("oc-ring").toString();
		transport = TransportModule.start(config, handlers, new BackendLifecycle(), mainThread);
		handlers.register("echo", ThreadAffinity.ANY, args -> args.raw());
		handlers.register("main.thread", ThreadAffinity.MAIN, args -> Thread.currentThread().getName());
		handlers.register("boom", ThreadAffinity.MAIN, args -> {
			throw new IllegalStateException("kaboom");
		});
	}

	@AfterEach
	void stop() {
		transport.close();
		mainThread.shutdownNow();
	}

	private static void await(BooleanSupplier condition) throws InterruptedException {
		long deadline = System.nanoTime() + 5_000_000_000L;
		while (!condition.getAsBoolean()) {
			assertTrue(System.nanoTime() < deadline, "condition not met in time");
			Thread.sleep(5);
		}
	}

	@Test
	void helloAnnouncesProtocolShmAndSortedOps() throws IOException {
		try (TestClient client = new TestClient(transport.socketPath())) {
			Map<String, Object> hello = client.read();
			assertEquals("hello", hello.get("event"));
			@SuppressWarnings("unchecked")
			Map<String, Object> data = (Map<String, Object>) hello.get("data");
			assertEquals(1L, data.get("protocol"));
			assertEquals(config.sharedMemoryPath, data.get("shm"));
			assertEquals(List.of("boom", "echo", "main.thread"), data.get("ops"));
			assertEquals(ProcessHandle.current().pid(), data.get("pid"));
		}
	}

	@Test
	void requestsRunOnTheRightThreadAndReply() throws IOException {
		try (TestClient client = new TestClient(transport.socketPath())) {
			assertEquals(Map.of("a", List.of(1L, "b")), client.call("echo", Map.of("a", List.of(1, "b"))).get("result"));
			assertEquals("fake-main", client.call("main.thread", Map.of()).get("result"));
			Map<String, Object> failure = client.call("boom", Map.of());
			assertEquals(false, failure.get("ok"));
			assertEquals("kaboom", failure.get("error"));
			assertEquals("Unknown op: missing", client.call("missing", Map.of()).get("error"));
			assertEquals(true, client.call("echo", Map.of()).get("ok"), "connection survives handler errors");
		}
	}

	@Test
	void fireAndForgetAndBatch() throws IOException {
		try (TestClient client = new TestClient(transport.socketPath())) {
			client.send("boom", Map.of(), false);
			client.send("echo", Map.of(), false);
			List<Object> calls = List.of(Map.of("op", "echo", "args", Map.of("n", 1)), Map.of("op", "main.thread"));
			Map<String, Object> reply = client.call("batch", Map.of("calls", calls));
			assertEquals(List.of(Map.of("ok", true, "result", Map.of("n", 1L)), Map.of("ok", true, "result", "fake-main")),
				reply.get("result"));
		}
	}

	@Test
	void malformedMessageYieldsErrorEventAndConnectionSurvives() throws IOException {
		try (TestClient client = new TestClient(transport.socketPath())) {
			client.sendRaw(new byte[] {1, 0, 0, 0, (byte) 0xc1});
			client.sendRaw(mod.kelvinlby.opencrafter.transport.wire.WireFormat.message(List.of(1)));
			assertNotNull(client.event("error").get("data"));
			assertEquals("Message must be a map", ((Map<?, ?>) client.event("error").get("data")).get("message"));
			assertEquals(true, client.call("echo", Map.of()).get("ok"));
		}
	}

	@Test
	void eventsReachEveryConnection() throws Exception {
		try (TestClient a = new TestClient(transport.socketPath()); TestClient b = new TestClient(transport.socketPath())) {
			// hello is sent after a connection is registered, so both now receive broadcasts.
			a.read();
			b.read();
			transport.events().publish("chat", Map.of("text", "hi"));
			assertEquals(Map.of("text", "hi"), a.event("chat").get("data"));
			assertEquals(Map.of("text", "hi"), b.event("chat").get("data"));
		}
	}

	@Test
	void commitAnnouncesFrameEvent() throws Exception {
		try (TestClient client = new TestClient(transport.socketPath())) {
			client.read();
			await(() -> transport.frames().hasReaders());
			FrameSlot slot = transport.frames().acquire(List.of(new StreamSpec("rgb", DType.UINT8, 4, 4, 4)));
			slot.commit(41, 0);
			Map<?, ?> data = (Map<?, ?>) client.event("frame").get("data");
			assertEquals(41L, data.get("index"));
			assertEquals(1L, data.get("seq"));
		}
	}

	@Test
	void concurrentConnectionsGetTheirOwnReplies() throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(4);
		try {
			List<Future<?>> runs = new ArrayList<>();
			for (int c = 0; c < 4; c++) {
				int client = c;
				runs.add(pool.submit(() -> {
					try (TestClient connection = new TestClient(transport.socketPath())) {
						for (int i = 0; i < 200; i++) {
							String op = i % 2 == 0 ? "echo" : "main.thread";
							Map<String, Object> reply = connection.call(op, Map.of("c", client, "i", i));
							Object expected = i % 2 == 0 ? Map.of("c", (long) client, "i", (long) i) : "fake-main";
							assertEquals(expected, reply.get("result"));
						}
					}
					return null;
				}));
			}
			for (Future<?> run : runs) {
				run.get();
			}
		} finally {
			pool.shutdownNow();
		}
	}

	@Test
	void disconnectClearsReadersAndCloseDeletesFiles() throws Exception {
		TestClient client = new TestClient(transport.socketPath());
		client.read();
		await(() -> transport.frames().hasReaders());
		client.close();
		await(() -> !transport.frames().hasReaders());
		Path socket = transport.socketPath();
		Path ring = transport.ringPath();
		assertTrue(Files.exists(socket));
		assertTrue(Files.exists(ring));
		transport.close();
		assertFalse(Files.exists(socket));
		assertFalse(Files.exists(ring));
	}

	@Test
	void staleSocketFileIsReplaced() throws Exception {
		transport.close();
		Files.writeString(Path.of(config.socketPath), "stale");
		transport = TransportModule.start(config, handlers, new BackendLifecycle(), mainThread);
		try (TestClient client = new TestClient(transport.socketPath())) {
			assertEquals("hello", client.read().get("event"));
		}
	}

	@Test
	void publishWithoutConnectionsIsANoOp() {
		assertDoesNotThrow(() -> transport.events().publish("x", Map.of("bad", new Object())));
		assertFalse(transport.frames().hasReaders());
	}
}
