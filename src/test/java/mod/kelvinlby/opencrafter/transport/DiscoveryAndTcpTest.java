package mod.kelvinlby.opencrafter.transport;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import mod.kelvinlby.opencrafter.api.BackendLifecycle;
import mod.kelvinlby.opencrafter.api.ThreadAffinity;
import mod.kelvinlby.opencrafter.api.handler.HandlerRegistry;
import mod.kelvinlby.opencrafter.core.config.TransportConfig;
import mod.kelvinlby.opencrafter.transport.server.Endpoint;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.*;

@Timeout(30)
class DiscoveryAndTcpTest {
	@TempDir
	Path dir;
	private final ExecutorService mainThread = Executors.newSingleThreadExecutor();
	private final HandlerRegistry handlers = new HandlerRegistry();
	private TransportModule transport;

	@AfterEach
	void stop() {
		if (transport != null) {
			transport.close();
		}
		mainThread.shutdownNow();
	}

	private TransportModule start(TransportConfig config) {
		handlers.register("echo", ThreadAffinity.ANY, args -> args.raw());
		return TransportModule.start(config, handlers, new BackendLifecycle(), mainThread, dir);
	}

	private static TransportConfig tcp() {
		TransportConfig config = new TransportConfig();
		config.listen = TransportConfig.Listen.TCP;
		return config;
	}

	@Test
	void defaultsLiveInTheDiscoveryDirectory() throws IOException {
		TransportConfig config = new TransportConfig();
		config.listen = TransportConfig.Listen.UNIX;
		transport = start(config);
		Endpoint.Unix endpoint = (Endpoint.Unix) transport.endpoint();
		assertEquals(dir, endpoint.path().getParent());
		JsonObject info = readDiscovery();
		assertEquals(endpoint.uri(), info.get("endpoint").getAsString());
		assertTrue(info.get("token").isJsonNull());
		assertEquals(transport.ringPath().toString(), info.get("shm").getAsString());
		assertEquals(ProcessHandle.current().pid(), info.get("pid").getAsLong());
	}

	@Test
	void tcpRequiresTheTokenFromTheDiscoveryFile() throws IOException {
		transport = start(tcp());
		JsonObject info = readDiscovery();
		assertTrue(info.get("endpoint").getAsString().startsWith("tcp:127.0.0.1:"));
		String token = info.get("token").getAsString();
		assertEquals(token, transport.token());
		try (TestClient client = new TestClient(transport.endpoint(), token)) {
			assertEquals("hello", client.read().get("event"));
			assertEquals(Map.of("a", 1L), client.call("echo", Map.of("a", 1)).get("result"));
		}
	}

	@Test
	void tcpClosesConnectionsWithAWrongToken() throws IOException {
		transport = start(tcp());
		try (TestClient client = new TestClient(transport.endpoint(), "wrong")) {
			assertThrows(IOException.class, client::read);
		}
	}

	@Test
	void tcpClosesConnectionsThatSkipAuth() throws IOException {
		transport = start(tcp());
		try (TestClient client = new TestClient(transport.endpoint())) {
			client.send("echo", Map.of(), true);
			assertThrows(IOException.class, client::read);
		}
	}

	@Test
	void configuredTokenIsUsed() throws IOException {
		TransportConfig config = tcp();
		config.token = "secret";
		transport = start(config);
		try (TestClient client = new TestClient(transport.endpoint(), "secret")) {
			assertEquals("hello", client.read().get("event"));
		}
	}

	@Test
	void reconfigureMovesTheEndpointAndRewritesDiscovery() throws IOException {
		TransportConfig config = new TransportConfig();
		config.listen = TransportConfig.Listen.UNIX;
		transport = start(config);
		Path oldSocket = ((Endpoint.Unix) transport.endpoint()).path();
		transport.reconfigure(tcp());
		assertFalse(Files.exists(oldSocket));
		assertInstanceOf(Endpoint.Tcp.class, transport.endpoint());
		assertEquals(transport.endpoint().uri(), readDiscovery().get("endpoint").getAsString());
		try (TestClient client = new TestClient(transport.endpoint(), transport.token())) {
			assertEquals("hello", client.read().get("event"));
		}
	}

	@Test
	void staleEntriesAreRemovedButLiveOnesKept() throws IOException {
		Files.createDirectories(dir);
		Files.writeString(dir.resolve("1-dead.json"), "{}");
		Files.writeString(dir.resolve("1-dead.lock"), "");
		Files.writeString(dir.resolve("1-dead.sock"), "");
		TransportConfig config = new TransportConfig();
		config.listen = TransportConfig.Listen.UNIX;
		transport = start(config);
		assertFalse(Files.exists(dir.resolve("1-dead.json")));
		assertFalse(Files.exists(dir.resolve("1-dead.sock")));
		assertFalse(Files.exists(dir.resolve("1-dead.lock")));
		assertTrue(Files.exists(transport.discoveryFile()));
	}

	private JsonObject readDiscovery() throws IOException {
		return JsonParser.parseString(Files.readString(transport.discoveryFile())).getAsJsonObject();
	}
}
