package mod.kelvinlby.crafter.connector;

import com.google.gson.JsonElement;
import mod.kelvinlby.crafter.OpenCrafter;
import net.minecraft.client.MinecraftClient;

import java.io.IOException;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Unix domain socket server for external communication.
 * <p>
 * Provides a non-blocking, multi-client socket server that listens at
 * {@code FOLDER/connector.socket} and dispatches JSON-RPC requests to the
 * {@link CommandRegistry}.
 * </p>
 *
 * <h3>Features</h3>
 * <ul>
 *   <li>Non-blocking NIO with selector — does not block the game thread</li>
 *   <li>Multiple concurrent client connections</li>
 *   <li>Automatic socket file cleanup on shutdown</li>
 *   <li>Game-thread safety — handlers are scheduled on the main thread</li>
 * </ul>
 *
 * <h3>Usage</h3>
 * <pre>
 * // Register commands via CommandRegistry before or after starting
 * CommandRegistry.register(
 *     CommandSpec.of("get_fps").description("Current FPS").build(),
 *     ctx -> new JsonPrimitive(MinecraftClient.getInstance().getCurrentFps())
 * );
 *
 * // Start the server
 * SocketConnector.start(FOLDER);
 *
 * // Stop the server
 * SocketConnector.stop();
 * </pre>
 */
public final class SocketConnector {

    private static final String SOCKET_FILENAME = "connector.socket";
    private static final int BUFFER_SIZE = 65536; // 64KB buffer for messages
    private static final int TICK_MS = 10; // 100Hz polling

    private static ServerSocketChannel serverChannel;
    private static Selector selector;
    private static Path socketPath;
    private static Thread serverThread;
    private static final AtomicBoolean running = new AtomicBoolean(false);

    // Client write queues: channel -> queue of pending messages
    private static final Map<SocketChannel, LinkedBlockingQueue<String>> clientQueues = new ConcurrentHashMap<>();

    // Connection state tracking
    private static final Map<SocketChannel, StringBuilder> receiveBuffers = new ConcurrentHashMap<>();

    private SocketConnector() {
        // Utility class
    }

    /**
     * Starts the Unix domain socket server.
     *
     * @param folder the mod data folder (e.g., .minecraft/open-crafter)
     * @throws IOException if server socket cannot be created
     */
    public static void start(Path folder) throws IOException {
        if (running.get()) {
            OpenCrafter.LOGGER.warn("SocketConnector is already running");
            return;
        }

        socketPath = folder.resolve(SOCKET_FILENAME);
        
        // Clean up existing socket file
        if (Files.exists(socketPath)) {
            Files.delete(socketPath);
            OpenCrafter.LOGGER.info("Removed stale socket file: {}", socketPath);
        }

        // Create Unix domain socket
        UnixDomainSocketAddress address = UnixDomainSocketAddress.of(socketPath);
        serverChannel = ServerSocketChannel.open(StandardProtocolFamily.UNIX);
        serverChannel.configureBlocking(false);
        serverChannel.bind(address);

        selector = Selector.open();
        serverChannel.register(selector, SelectionKey.OP_ACCEPT);

        running.set(true);
        
        // Start server thread (daemon - won't prevent JVM shutdown)
        serverThread = new Thread(SocketConnector::serverLoop, "socket-connector-server");
        serverThread.setDaemon(true);
        serverThread.start();

        OpenCrafter.LOGGER.info("SocketConnector started at {}", socketPath.toAbsolutePath());
    }

    /**
     * Stops the socket server and cleans up resources.
     */
    public static void stop() {
        if (!running.get()) {
            return;
        }

        OpenCrafter.LOGGER.info("Stopping SocketConnector...");
        
        running.set(false);
        
        // Wake up selector to unblock the server thread
        if (selector != null && selector.isOpen()) {
            selector.wakeup();
        }

        // Wait for server thread to finish
        if (serverThread != null) {
            try {
                serverThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                OpenCrafter.LOGGER.warn("Interrupted while waiting for server thread");
            }
        }

        // Close all client connections
        for (SocketChannel channel : clientQueues.keySet()) {
            closeChannel(channel);
        }
        clientQueues.clear();
        receiveBuffers.clear();

        // Close server channel and selector
        try {
            if (serverChannel != null && serverChannel.isOpen()) {
                serverChannel.close();
            }
            if (selector != null && selector.isOpen()) {
                selector.close();
            }
        } catch (IOException e) {
            OpenCrafter.LOGGER.error("Error closing server resources", e);
        }

        // Delete socket file
        if (socketPath != null && Files.exists(socketPath)) {
            try {
                Files.delete(socketPath);
                OpenCrafter.LOGGER.info("Deleted socket file: {}", socketPath);
            } catch (IOException e) {
                OpenCrafter.LOGGER.error("Failed to delete socket file", e);
            }
        }

        serverChannel = null;
        selector = null;
        socketPath = null;
        serverThread = null;

        OpenCrafter.LOGGER.info("SocketConnector stopped");
    }

    /**
     * Main server loop - runs on dedicated thread.
     * Uses NIO selector for non-blocking I/O.
     */
    private static void serverLoop() {
        while (running.get()) {
            try {
                // Wait for events with timeout (allows checking running flag)
                int readyChannels = selector.select(TICK_MS);
                
                if (readyChannels == 0) {
                    // No events - process pending writes
                    processPendingWrites();
                    continue;
                }

                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> iter = selectedKeys.iterator();

                while (iter.hasNext()) {
                    SelectionKey key = iter.next();
                    iter.remove();

                    if (!key.isValid()) {
                        continue;
                    }

                    if (key.isAcceptable()) {
                        handleAccept(key);
                    } else if (key.isReadable()) {
                        handleRead(key);
                    } else if (key.isWritable()) {
                        handleWrite(key);
                    }
                }

                // Always process pending writes after handling events
                processPendingWrites();

            } catch (IOException e) {
                OpenCrafter.LOGGER.error("Error in server loop", e);
                // Continue running - transient errors shouldn't kill the server
            } catch (Exception e) {
                OpenCrafter.LOGGER.error("Unexpected error in server loop", e);
            }
        }
    }

    /**
     * Handles a new client connection.
     */
    private static void handleAccept(SelectionKey key) throws IOException {
        ServerSocketChannel server = (ServerSocketChannel) key.channel();
        SocketChannel client = server.accept();
        client.configureBlocking(false);
        
        // Register for read and write events
        client.register(selector, SelectionKey.OP_READ | SelectionKey.OP_WRITE);
        
        // Initialize per-client state
        clientQueues.put(client, new LinkedBlockingQueue<>());
        receiveBuffers.put(client, new StringBuilder());
        
        OpenCrafter.LOGGER.debug("Client connected: {}", client.getRemoteAddress());
    }

    /**
     * Handles incoming data from a client.
     * Parses JSON-RPC requests and sends responses.
     */
    private static void handleRead(SelectionKey key) {
        SocketChannel client = (SocketChannel) key.channel();
        StringBuilder buffer = receiveBuffers.get(client);
        
        if (buffer == null) {
            return;
        }

        ByteBuffer readBuffer = ByteBuffer.allocate(BUFFER_SIZE);
        
        try {
            int bytesRead = client.read(readBuffer);
            
            if (bytesRead == -1) {
                // End of stream - client disconnected
                closeChannel(client);
                return;
            }
            
            if (bytesRead > 0) {
                readBuffer.flip();
                byte[] data = new byte[bytesRead];
                readBuffer.get(data);
                buffer.append(new String(data, StandardCharsets.UTF_8));
                
                // Process complete messages (delimited by newline)
                processMessages(client, buffer);
            }
            
        } catch (IOException e) {
            OpenCrafter.LOGGER.debug("Read error from client", e);
            closeChannel(client);
        }
    }

    /**
     * Processes complete JSON-RPC messages from the buffer.
     * Messages are newline-delimited.
     */
    private static void processMessages(SocketChannel client, StringBuilder buffer) {
        while (true) {
            int newlineIndex = buffer.indexOf("\n");
            if (newlineIndex == -1) {
                break; // No complete message yet
            }

            String message = buffer.substring(0, newlineIndex).trim();
            buffer.delete(0, newlineIndex + 1);

            if (message.isEmpty()) {
                continue;
            }

            // Parse and handle the JSON-RPC request
            handleRequest(client, message);
        }
    }

    /**
     * Handles a single JSON-RPC request and queues the response.
     * Dispatches to {@link CommandRegistry} after protocol-level validation.
     */
    private static void handleRequest(SocketChannel client, String message) {
        JsonRpcProtocol.RpcRequest request = JsonRpcProtocol.parseRequest(message);

        if (request == null) {
            queueResponse(client, JsonRpcProtocol.createError(
                JsonRpcProtocol.ERROR_PARSE, "Invalid JSON", null));
            return;
        }

        if (request.hasError()) {
            queueResponse(client, JsonRpcProtocol.createError(
                request.errorCode, request.errorMessage, request.id));
            return;
        }

        // Schedule on the game's main thread — all handlers may touch game state
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.isOnThread()) {
            dispatchAndRespond(client, request);
        } else {
            mc.execute(() -> dispatchAndRespond(client, request));
        }
    }

    /**
     * Dispatches the request through {@link CommandRegistry} and queues the response.
     * Must be called on the game main thread.
     */
    private static void dispatchAndRespond(SocketChannel client, JsonRpcProtocol.RpcRequest request) {
        try {
            JsonElement result = CommandRegistry.dispatch(request);
            if (request.id != null) {
                queueResponse(client, JsonRpcProtocol.createResponse(result, request.id));
            }
        } catch (CommandHandler.CommandException e) {
            OpenCrafter.LOGGER.debug("Command '{}' error: {}", request.method, e.getMessage());
            if (request.id != null) {
                queueResponse(client, JsonRpcProtocol.createError(e.getCode(), e.getMessage(), request.id));
            }
        } catch (Exception e) {
            OpenCrafter.LOGGER.error("Unexpected error handling command '{}'", request.method, e);
            if (request.id != null) {
                queueResponse(client, JsonRpcProtocol.createError(
                    JsonRpcProtocol.ERROR_INTERNAL, "Internal error: " + e.getMessage(), request.id));
            }
        }
    }

    /**
     * Handles write readiness - sends pending messages to client.
     */
    private static void handleWrite(SelectionKey key) throws IOException {
        SocketChannel client = (SocketChannel) key.channel();
        LinkedBlockingQueue<String> queue = clientQueues.get(client);
        
        if (queue == null) {
            return;
        }

        String message = queue.poll();
        if (message != null) {
            ByteBuffer writeBuffer = ByteBuffer.wrap((message + "\n").getBytes(StandardCharsets.UTF_8));
            client.write(writeBuffer);
        }
    }

    /**
     * Processes all pending writes (for channels with no active write event).
     */
    private static void processPendingWrites() {
        for (Map.Entry<SocketChannel, LinkedBlockingQueue<String>> entry : clientQueues.entrySet()) {
            SocketChannel client = entry.getKey();
            LinkedBlockingQueue<String> queue = entry.getValue();
            
            if (!client.isConnected() || !clientQueues.containsKey(client)) {
                continue;
            }

            // Try to write if there's pending data
            if (!queue.isEmpty()) {
                SelectionKey key = client.keyFor(selector);
                if (key != null && key.isValid() && key.isWritable()) {
                    try {
                        handleWrite(key);
                    } catch (IOException e) {
                        OpenCrafter.LOGGER.debug("Write error", e);
                        closeChannel(client);
                    }
                }
            }
        }
    }

    /**
     * Queues a response message for sending to a client.
     */
    private static void queueResponse(SocketChannel client, String message) {
        LinkedBlockingQueue<String> queue = clientQueues.get(client);
        if (queue != null) {
            queue.offer(message);
            // Wake up selector to process the write
            if (selector != null) {
                selector.wakeup();
            }
        }
    }

    /**
     * Closes a client connection and cleans up associated resources.
     */
    private static void closeChannel(SocketChannel client) {
        try {
            client.close();
        } catch (IOException e) {
            // Ignore
        }
        
        clientQueues.remove(client);
        receiveBuffers.remove(client);
        
        SelectionKey key = client.keyFor(selector);
        if (key != null) {
            key.cancel();
        }

        OpenCrafter.LOGGER.debug("Client disconnected");
    }

    /**
     * Checks if the socket server is running.
     */
    public static boolean isRunning() {
        return running.get();
    }

    /**
     * Gets the socket file path.
     */
    public static Path getSocketPath() {
        return socketPath;
    }

}
