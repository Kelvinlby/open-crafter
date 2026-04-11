package mod.kelvinlby.crafter.registry;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mod.kelvinlby.crafter.OpenCrafter;
import mod.kelvinlby.crafter.connector.*;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.util.ScreenshotRecorder;
import net.minecraft.util.Util;

import java.nio.channels.SocketChannel;
import java.nio.file.Files;
import java.nio.file.Path;

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

                    SocketChannel socket = ctx.socket;
                    JsonElement requestId = ctx.requestId;

                    // ScreenshotRecorder delivers the NativeImage on the render thread once
                    // the GPU readback fence completes. PNG encoding is expensive, so hand
                    // the image off to the IO worker pool to write it there. The socket
                    // response is only sent after the IO worker finishes writing the file,
                    // guaranteeing the path is on disk by the time the client sees it.
                    ScreenshotRecorder.takeScreenshot(mc.getFramebuffer(), image ->
                            Util.getIoWorkerExecutor().execute(() -> {
                                try (NativeImage img = image) {
                                    img.writeTo(out.toFile());
                                } catch (Exception e) {
                                    OpenCrafter.LOGGER.error("vision: failed to save screenshot", e);
                                    SocketConnector.respondError(socket, requestId,
                                            JsonRpcProtocol.ERROR_INTERNAL,
                                            "Failed to save screenshot: " + e.getMessage());
                                    return;
                                }
                                JsonObject result = new JsonObject();
                                result.addProperty("path", out.toAbsolutePath().toString());
                                SocketConnector.respond(socket, requestId, result);
                            }));

                    return CommandRegistry.ASYNC;
                }
        );
    }
}
