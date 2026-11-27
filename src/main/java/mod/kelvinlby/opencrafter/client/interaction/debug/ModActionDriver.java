package mod.kelvinlby.opencrafter.client.interaction.debug;

import mod.kelvinlby.opencrafter.OpenCrafter;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.phys.Vec3;

/**
 * Dev-only test driver that acts the way another mod would, never through the crosshair or the
 * keyboard: enabled with {@code -Dopencrafter.interaction.debugModActions=N}, every N ticks it runs
 * the next step of a cycle on blocks diagonally ahead of the player (in view, off the crosshair) or
 * the nearest mob. {@code -Dopencrafter.interaction.debugModSteps=a,b,...} picks the cycle from
 * {@link ModSteps} (default: place, attack, rawplace, break).
 * With {@code -Dopencrafter.interaction.debugSpoofRotation=T} each step first sends the server a
 * rotation toward its target (the player's own view stays) and the player's own rotation back T
 * ticks later, like a silent-rotation mod.
 */
public final class ModActionDriver {
	private static final int SETTLE_TICKS = 100;
	private static final String DEFAULT_STEPS = "place,attack,rawplace,break";

	private final int period;
	private final String[] cycle;
	private final ModSteps steps = new ModSteps();
	private final SpoofedRotation spoof;
	private int ticks;
	private int next;

	private ModActionDriver(int period, String[] cycle, int spoofTicks) {
		this.period = period;
		this.cycle = cycle;
		this.spoof = new SpoofedRotation(spoofTicks);
	}

	public static void installIfRequested() {
		int period = Integer.getInteger("opencrafter.interaction.debugModActions", 0);
		if (period > 0) {
			String[] cycle = System.getProperty("opencrafter.interaction.debugModSteps", DEFAULT_STEPS).split(",");
			ModActionDriver driver = new ModActionDriver(period, cycle, Integer.getInteger("opencrafter.interaction.debugSpoofRotation", 0));
			ClientTickEvents.END_CLIENT_TICK.register(driver::tick);
			OpenCrafter.LOGGER.warn("Interaction debug driver enabled: a mod-style action every {} ticks", period);
		}
	}

	private void tick(Minecraft minecraft) {
		LocalPlayer player = minecraft.player;
		MultiPlayerGameMode gameMode = minecraft.gameMode;
		if (player == null || gameMode == null || minecraft.gui.screen() != null) {
			return;
		}
		ticks++;
		spoof.restoreIfDue(player, ticks);
		steps.tick(player, ticks);
		if (ticks > SETTLE_TICKS && ticks % period == 0) {
			runNext(minecraft, player, gameMode);
		}
	}

	private void runNext(Minecraft minecraft, LocalPlayer player, MultiPlayerGameMode gameMode) {
		String step = cycle[next++ % cycle.length].trim();
		Vec3 target = steps.target(minecraft, player, step);
		if (target != null) {
			spoof.aimAt(player, target, ticks);
		}
		steps.run(minecraft, player, gameMode, step, ticks);
	}
}
