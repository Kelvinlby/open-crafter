package mod.kelvinlby.opencrafter.client.vision.debug;

import mod.kelvinlby.opencrafter.OpenCrafter;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

import java.util.List;

/**
 * Debug-only test driver for verifying the vision streams in a dev client, enabled with
 * {@code -Dopencrafter.vision.debugWorld=<name>}: opens (or creates) that singleplayer world,
 * runs {@code -Dopencrafter.vision.debugCommands} once (';'-separated, '+' stands for a space so
 * the value survives {@code JAVA_TOOL_OPTIONS}), and spins the camera by
 * {@code -Dopencrafter.vision.debugSpin} degrees per tick so flow has something to measure.
 */
public final class DebugScenario {
	private static final int SETTLE_TICKS = 60;

	private final String worldName;
	private final List<String> commands;
	private final float spinDegreesPerTick;
	private boolean worldRequested;
	private int ticksInWorld;

	private DebugScenario(String worldName, List<String> commands, float spinDegreesPerTick) {
		this.worldName = worldName;
		this.commands = commands;
		this.spinDegreesPerTick = spinDegreesPerTick;
	}

	public static void installIfRequested() {
		String world = System.getProperty("opencrafter.vision.debugWorld");
		if (world == null) {
			return;
		}
		String commandList = System.getProperty("opencrafter.vision.debugCommands", "");
		float spin = Float.parseFloat(System.getProperty("opencrafter.vision.debugSpin", "0"));
		DebugScenario scenario = new DebugScenario(world, List.of(commandList.split(";")), spin);
		ClientTickEvents.END_CLIENT_TICK.register(scenario::tick);
		OpenCrafter.LOGGER.warn("Vision debug scenario enabled for world '{}'", world);
	}

	private void tick(Minecraft minecraft) {
		if (!worldRequested && minecraft.gui.screen() instanceof TitleScreen) {
			worldRequested = true;
			openWorld(minecraft);
		}
		LocalPlayer player = minecraft.player;
		if (player == null || minecraft.gui.screen() != null) {
			return;
		}
		ticksInWorld++;
		if (ticksInWorld == SETTLE_TICKS || repeatsNow()) {
			runCommands(player);
		}
		if (ticksInWorld > SETTLE_TICKS) {
			player.setYRot(player.getYRot() + spinDegreesPerTick);
			strafe(player);
		}
	}

	/** {@code -Dopencrafter.vision.debugCommandPeriod=N} re-runs the commands every N ticks. */
	private boolean repeatsNow() {
		int period = Integer.getInteger("opencrafter.vision.debugCommandPeriod", 0);
		return period > 0 && ticksInWorld > SETTLE_TICKS && (ticksInWorld - SETTLE_TICKS) % period == 0;
	}

	/** Moves the camera sideways by {@code -Dopencrafter.vision.debugStrafe} blocks per tick (camera translation). */
	private static void strafe(LocalPlayer player) {
		double step = Double.parseDouble(System.getProperty("opencrafter.vision.debugStrafe", "0"));
		if (step != 0) {
			player.setPos(player.getX() + step, player.getY(), player.getZ());
		}
	}

	private void openWorld(Minecraft minecraft) {
		if (minecraft.getLevelSource().levelExists(worldName)) {
			minecraft.createWorldOpenFlows().openWorld(worldName, () -> minecraft.gui.setScreen(new TitleScreen()));
			return;
		}
		LevelSettings settings = new LevelSettings(worldName, GameType.CREATIVE, LevelSettings.DifficultySettings.DEFAULT, true, WorldDataConfiguration.DEFAULT);
		minecraft.createWorldOpenFlows().createFreshLevel(worldName, settings, new WorldOptions(12345L, true, false),
			WorldPresets::createNormalWorldDimensions, new TitleScreen());
	}

	private void runCommands(LocalPlayer player) {
		for (String command : commands) {
			if (!command.isBlank()) {
				player.connection.sendCommand(command.strip().replace('+', ' '));
			}
		}
	}
}
