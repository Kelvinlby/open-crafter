package mod.kelvinlby.crafter.engine;

import mod.kelvinlby.crafter.OpenCrafter;
import net.minecraft.client.MinecraftClient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Manages the lifecycle of the open-crafter-engine process.
 * Ensures the engine runs on a separate thread and is properly terminated
 * when the game closes or crashes, preventing zombie processes.
 */
public class EngineProcessManager {
    
    private static final String ENGINE_BINARY_NAME = "open-crafter-engine";
    private static final AtomicBoolean isRunning = new AtomicBoolean(false);
    private static Process engineProcess = null;
    private static ExecutorService processExecutor = null;
    private static CompletableFuture<Void> processFuture = null;
    
    /**
     * Starts the engine process if all initialization checks pass.
     * Runs on a separate thread to avoid blocking the Minecraft main thread.
     * 
     * @param openCrafterDir The open-crafter directory (e.g., .minecraft/open-crafter)
     */
    public static void startEngine(Path openCrafterDir) {
        if (isRunning.get()) {
            OpenCrafter.LOGGER.warn("Engine process is already running");
            return;
        }
        
        Path engineDir = openCrafterDir.resolve("engine");
        Path engineBinary = findEngineBinary(engineDir);
        
        if (engineBinary == null || !Files.exists(engineBinary)) {
            OpenCrafter.LOGGER.error("Engine binary not found at {}", engineDir.resolve(ENGINE_BINARY_NAME));
            return;
        }
        
        if (!Files.isExecutable(engineBinary)) {
            OpenCrafter.LOGGER.error("Engine binary is not executable: {}", engineBinary);
            return;
        }
        
        OpenCrafter.LOGGER.info("Starting engine process: {}", engineBinary);
        startProcess(engineBinary);
    }
    
    /**
     * Finds the engine binary in the engine directory.
     * Handles both Windows (.exe) and Unix-like systems.
     */
    private static Path findEngineBinary(Path engineDir) {
        // Try platform-specific binary name first
        String os = System.getProperty("os.name").toLowerCase();
        if (os.contains("win")) {
            Path windowsBinary = engineDir.resolve(ENGINE_BINARY_NAME + ".exe");
            if (Files.exists(windowsBinary)) {
                return windowsBinary;
            }
        }
        
        // Try the base name
        Path unixBinary = engineDir.resolve(ENGINE_BINARY_NAME);
        if (Files.exists(unixBinary)) {
            return unixBinary;
        }
        
        return null;
    }
    
    private static void startProcess(Path engineBinary) {
        try {
            ProcessBuilder processBuilder = new ProcessBuilder(engineBinary.toString());
            processBuilder.directory(engineBinary.getParent().toFile());
            processBuilder.inheritIO(); // Inherit stdout/stderr for debugging
            
            engineProcess = processBuilder.start();
            isRunning.set(true);
            
            // Create executor for monitoring the process
            processExecutor = Executors.newSingleThreadExecutor(r -> {
                Thread t = new Thread(r, "open-crafter-engine-monitor");
                t.setDaemon(true); // Daemon thread - won't prevent JVM shutdown
                return t;
            });
            
            // Monitor the process asynchronously
            processFuture = CompletableFuture.runAsync(() -> {
                try {
                    // Wait for process to exit
                    int exitCode = engineProcess.waitFor();
                    isRunning.set(false);
                    
                    if (exitCode == 0) {
                        OpenCrafter.LOGGER.info("Engine process exited normally");
                    } else {
                        OpenCrafter.LOGGER.warn("Engine process exited with code: {}", exitCode);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    OpenCrafter.LOGGER.warn("Engine process monitor interrupted");
                }
            }, processExecutor);
            
            OpenCrafter.LOGGER.info("Engine process started successfully (PID: {})", engineProcess.pid());
            
        } catch (IOException e) {
            isRunning.set(false);
            OpenCrafter.LOGGER.error("Failed to start engine process", e);
        }
    }
    
    /**
     * Gracefully shuts down the engine process.
     * Should be called when the game is closing.
     */
    public static void shutdown() {
        if (!isRunning.get() || engineProcess == null) {
            return;
        }
        
        OpenCrafter.LOGGER.info("Shutting down engine process...");
        
        try {
            // Try graceful shutdown first
            engineProcess.destroy();
            
            // Wait for process to terminate (with timeout)
            boolean terminated = engineProcess.waitFor(5, TimeUnit.SECONDS);
            
            if (!terminated) {
                // Force kill if still running
                OpenCrafter.LOGGER.warn("Engine process did not terminate gracefully, forcing...");
                engineProcess.destroyForcibly();
                
                boolean forciblyTerminated = engineProcess.waitFor(3, TimeUnit.SECONDS);
                if (!forciblyTerminated) {
                    OpenCrafter.LOGGER.error("Failed to terminate engine process after force kill");
                }
            }
            
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            OpenCrafter.LOGGER.error("Interrupted while shutting down engine process", e);
            // Still try to force kill
            if (engineProcess != null) {
                engineProcess.destroyForcibly();
            }
        } finally {
            isRunning.set(false);
            engineProcess = null;
            
            // Shutdown executor
            if (processExecutor != null) {
                processExecutor.shutdownNow();
                processExecutor = null;
            }
            processFuture = null;
            
            OpenCrafter.LOGGER.info("Engine process shutdown complete");
        }
    }
    
    /**
     * Checks if the engine process is currently running.
     */
    public static boolean isEngineRunning() {
        return isRunning.get() && engineProcess != null && engineProcess.isAlive();
    }
    
    /**
     * Registers shutdown hooks to ensure the engine process is terminated
     * even if Minecraft crashes or is force-closed.
     */
    public static void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            OpenCrafter.LOGGER.info("JVM shutdown hook triggered");
            shutdown();
        }, "open-crafter-shutdown-hook"));
    }
}
