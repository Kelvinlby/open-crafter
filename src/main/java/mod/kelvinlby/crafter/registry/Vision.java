package mod.kelvinlby.crafter.registry;

import com.google.gson.JsonObject;
import mod.kelvinlby.crafter.OpenCrafter;
import mod.kelvinlby.crafter.connector.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicReference;

public final class Vision {
    public static void register() {
        vision();
        OpenCrafter.LOGGER.info("Vision Registry: registered 1 command");
    }

    private static void vision() {
        CommandRegistry.register(
                CommandSpec.of("vision")
                        .description("Capture the current game view and save it to the cache folder")
                        .build(),
                ctx -> {
                    MinecraftClient mc = ctx.client;
                    if (mc.getFramebuffer() == null) {
                        throw new CommandHandler.CommandException("Framebuffer not available");
                    }

                    Path cacheDir = OpenCrafter.FOLDER.resolve("cache");
                    Files.createDirectories(cacheDir);
                    Path out = cacheDir.resolve(System.currentTimeMillis() + ".png");

                    AtomicReference<Exception> err = new AtomicReference<>();
                    ScreenshotRecorder.takeScreenshot(mc.getFramebuffer(), image -> {
                        try (NativeImage img = image) {
                            img.writeTo(out.toFile());
                        } catch (Exception e) {
                            err.set(e);
                        }
                    });

                    if (err.get() != null) {
                        throw new CommandHandler.CommandException("Failed to save screenshot: " + err.get().getMessage());
                    }

                    JsonObject result = new JsonObject();
                    result.addProperty("path", out.toAbsolutePath().toString());
                    return result;
                }
        );
    }
}
