package mod.kelvinlby.crafter.browser;

import net.ccbluex.liquidbounce.mcef.MCEF;

import java.io.File;
import java.io.IOException;

import static mod.kelvinlby.crafter.OpenCrafter.LOGGER;

/**
 * Manages the MCEF (Chromium) browser lifecycle: init, per-frame tick, and shutdown.
 */
public class BrowserManager {
    private static boolean initialized = false;

    /**
     * Configure and initialize MCEF. Downloads CEF binaries on first run (~200 MB).
     * Must be called on the render thread.
     */
    public static void initialize(File gameDir) {
        if (initialized) return;

        File mcefDir = new File(gameDir, "mcef");
        File libDir = new File(mcefDir, "libraries");
        File cacheDir = new File(mcefDir, "cache/" + Long.toHexString(System.currentTimeMillis()));

        MCEF.INSTANCE.getSettings().setCacheDirectory(cacheDir);
        MCEF.INSTANCE.getSettings().setLibrariesDirectory(libDir);
        
        // CEF switches for Linux compatibility - force software rendering
        MCEF.INSTANCE.getSettings().appendCefSwitches("--no-proxy-server");
//        MCEF.INSTANCE.getSettings().appendCefSwitches("--disable-gpu");
//        MCEF.INSTANCE.getSettings().appendCefSwitches("--disable-gpu-compositing");
        // Force software output and painting
        MCEF.INSTANCE.getSettings().appendCefSwitches("--enable-begin-frame-scheduling");

        try {
            var resourceManager = MCEF.INSTANCE.newResourceManager();

            if (!resourceManager.isSystemCompatible()) {
                LOGGER.error("MCEF is not compatible with this system");
                return;
            }

            if (resourceManager.requiresDownload()) {
                LOGGER.info("Downloading CEF binaries (first run)...");
                resourceManager.downloadJcef();
                LOGGER.info("CEF download complete");
            }
        } catch (IOException e) {
            LOGGER.error("Failed to set up MCEF resources", e);
            return;
        }

        MCEF.INSTANCE.initialize();
        initialized = true;
        LOGGER.info("MCEF initialized");
    }

    /**
     * Pump the CEF message loop. Call once per render frame.
     */
    public static void tick() {
        if (initialized && MCEF.INSTANCE.isInitialized()) {
            try {
                MCEF.INSTANCE.getApp().getHandle().N_DoMessageLoopWork();
            } catch (Exception e) {
                LOGGER.error("CEF message loop error", e);
            }
        }
    }

    public static boolean isInitialized() {
        return initialized && MCEF.INSTANCE.isInitialized();
    }

    /**
     * Shut down MCEF. Call on game exit.
     */
    public static void shutdown() {
        if (initialized) {
            MCEF.INSTANCE.shutdown();
            initialized = false;
            LOGGER.info("MCEF shut down");
        }
    }
}
