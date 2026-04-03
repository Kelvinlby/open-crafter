package mod.kelvinlby.crafter.connector;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import mod.kelvinlby.crafter.OpenCrafter;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;

/**
 * Provides pre-registered command handlers for querying game state.
 * <p>
 * These handlers allow external processes to query the current game state
 * via the Unix domain socket connection.
 * </p>
 * 
 * <h3>Available Commands:</h3>
 * <ul>
 *   <li>{@code get_fps} - Get current FPS</li>
 *   <li>{@code get_player_pos} - Get player position</li>
 *   <li>{@code get_player_rotation} - Get player rotation (yaw, pitch)</li>
 *   <li>{@code get_player_health} - Get player health</li>
 *   <li>{@code get_player_inventory} - Get main inventory slots</li>
 *   <li>{@code get_player_hotbar} - Get hotbar slots (0-8)</li>
 *   <li>{@code get_biome} - Get biome at position</li>
 *   <li>{@code get_time} - Get world time</li>
 *   <li>{@code get_dimension} - Get current dimension</li>
 *   <li>{@code screenshot} - Capture and return screenshot as base64</li>
 * </ul>
 */
public final class GameStateProvider {

    private GameStateProvider() {
        // Utility class
    }

    /**
     * Registers all built-in game state handlers.
     */
    public static void registerAll() {
        registerFpsHandler();
        registerPlayerPositionHandler();
        registerPlayerRotationHandler();
        registerPlayerHealthHandler();
        registerPlayerInventoryHandler();
        registerPlayerHotbarHandler();
        registerBiomeHandler();
        registerTimeHandler();
        registerDimensionHandler();
        registerScreenshotHandler();
        
        OpenCrafter.LOGGER.info("GameStateProvider handlers registered");
    }

    private static void registerFpsHandler() {
        SocketConnector.registerHandler("get_fps", 0, args -> {
            int fps = MinecraftClient.getInstance().getCurrentFps();
            return new com.google.gson.JsonPrimitive(fps);
        });
    }

    private static void registerPlayerPositionHandler() {
        SocketConnector.registerHandler("get_player_pos", 0, args -> {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if (player == null) {
                throw CommandHandler.CommandException.invalidParams("Player not in world");
            }
            
            JsonObject result = new JsonObject();
            result.addProperty("x", player.getX());
            result.addProperty("y", player.getY());
            result.addProperty("z", player.getZ());
            return result;
        });
    }

    private static void registerPlayerRotationHandler() {
        SocketConnector.registerHandler("get_player_rotation", 0, args -> {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if (player == null) {
                throw CommandHandler.CommandException.invalidParams("Player not in world");
            }
            
            JsonObject result = new JsonObject();
            result.addProperty("yaw", player.getYaw());
            result.addProperty("pitch", player.getPitch());
            return result;
        });
    }

    private static void registerPlayerHealthHandler() {
        SocketConnector.registerHandler("get_player_health", 0, args -> {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if (player == null) {
                throw CommandHandler.CommandException.invalidParams("Player not in world");
            }
            
            JsonObject result = new JsonObject();
            result.addProperty("health", player.getHealth());
            result.addProperty("max_health", player.getMaxHealth());
            result.addProperty("absorption", player.getAbsorptionAmount());
            result.addProperty("armor", player.getArmor());
            return result;
        });
    }

    private static void registerPlayerInventoryHandler() {
        SocketConnector.registerHandler("get_player_inventory", 0, args -> {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if (player == null) {
                throw CommandHandler.CommandException.invalidParams("Player not in world");
            }
            
            JsonArray inventory = new JsonArray();
            for (int i = 0; i < player.getInventory().size(); i++) {
                ItemStack stack = player.getInventory().getStack(i);
                inventory.add(itemStackToJson(stack));
            }
            return inventory;
        });
    }

    private static void registerPlayerHotbarHandler() {
        SocketConnector.registerHandler("get_player_hotbar", 0, args -> {
            ClientPlayerEntity player = MinecraftClient.getInstance().player;
            if (player == null) {
                throw CommandHandler.CommandException.invalidParams("Player not in world");
            }
            
            JsonArray hotbar = new JsonArray();
            for (int i = 0; i < 9; i++) {
                ItemStack stack = player.getInventory().getStack(i);
                hotbar.add(itemStackToJson(stack));
            }
            return hotbar;
        });
    }

    private static void registerBiomeHandler() {
        SocketConnector.registerHandler("get_biome", 3, args -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null) {
                throw CommandHandler.CommandException.invalidParams("Not in a world");
            }
            
            int x = args.get(0).getAsInt();
            int y = args.get(1).getAsInt();
            int z = args.get(2).getAsInt();
            
            var biome = client.world.getBiome(new net.minecraft.util.math.BlockPos(x, y, z));
            Identifier biomeId = client.world.getRegistryManager()
                .getOrThrow(net.minecraft.registry.RegistryKeys.BIOME)
                .getId(biome.value());
            
            JsonObject result = new JsonObject();
            result.addProperty("id", biomeId.toString());
            return result;
        });
    }

    private static void registerTimeHandler() {
        SocketConnector.registerHandler("get_time", 0, args -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null) {
                throw CommandHandler.CommandException.invalidParams("Not in a world");
            }
            
            long time = client.world.getTimeOfDay();
            long dayTime = time % 24000;
            
            JsonObject result = new JsonObject();
            result.addProperty("total_time", time);
            result.addProperty("day_time", dayTime);
            result.addProperty("day", time / 24000);
            return result;
        });
    }

    private static void registerDimensionHandler() {
        SocketConnector.registerHandler("get_dimension", 0, args -> {
            MinecraftClient client = MinecraftClient.getInstance();
            if (client.world == null) {
                throw CommandHandler.CommandException.invalidParams("Player not in world");
            }
            
            Identifier dimId = client.world.getRegistryKey().getValue();
            return new com.google.gson.JsonPrimitive(dimId.toString());
        });
    }

    private static void registerScreenshotHandler() {
        SocketConnector.registerHandler("screenshot", 0, args -> {
            MinecraftClient client = MinecraftClient.getInstance();
            
            // Render context for screenshot
            int width = client.getWindow().getFramebufferWidth();
            int height = client.getWindow().getFramebufferHeight();
            
            // Allocate buffer for pixel data
            java.nio.ByteBuffer buffer = java.nio.ByteBuffer.allocateDirect(width * height * 4);
            
            // Use GL to read pixels
            org.lwjgl.opengl.GL11.glReadPixels(0, 0, width, height, 
                org.lwjgl.opengl.GL11.GL_RGBA, org.lwjgl.opengl.GL11.GL_UNSIGNED_BYTE, buffer);
            
            // Flip buffer to read from beginning
            buffer.flip();
            
            byte[] pixels = new byte[width * height * 4];
            buffer.get(pixels);
            
            // Create buffered image
            java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(
                width, height, java.awt.image.BufferedImage.TYPE_INT_RGB);
            
            // Convert RGBA to ARGB (flip vertically and convert format)
            for (int y = 0; y < height; y++) {
                for (int x = 0; x < width; x++) {
                    int idx = (y * width + x) * 4;
                    int r = pixels[idx] & 0xFF;
                    int g = pixels[idx + 1] & 0xFF;
                    int b = pixels[idx + 2] & 0xFF;
                    // Flip Y coordinate
                    image.setRGB(x, height - 1 - y, (0xFF << 24) | (r << 16) | (g << 8) | b);
                }
            }
            
            // Encode as PNG
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, "png", baos);
            byte[] pngData = baos.toByteArray();
            
            // Return as base64
            JsonObject result = new JsonObject();
            result.addProperty("format", "png");
            result.addProperty("width", width);
            result.addProperty("height", height);
            result.addProperty("data", JsonRpcProtocol.JsonUtils.toBase64(pngData));
            
            return result;
        });
    }

    /**
     * Converts an ItemStack to JSON.
     */
    private static JsonElement itemStackToJson(ItemStack stack) {
        JsonObject item = new JsonObject();
        
        if (stack.isEmpty()) {
            item.addProperty("empty", true);
            return item;
        }
        
        item.addProperty("empty", false);
        item.addProperty("count", stack.getCount());

        Identifier itemId = Registries.ITEM.getId(stack.getItem());
        item.addProperty("id", itemId.toString());
        item.addProperty("name", stack.getName().getString());

        // Check if stack has any components (1.21.11+ uses components instead of NBT)
        if (!stack.getComponents().isEmpty()) {
            item.addProperty("has_components", true);
        }

        return item;
    }

    /**
     * Helper to access RegistryKeys.
     */
    private static class RegistryKeys {
        // Unused - kept for reference
    }
}
