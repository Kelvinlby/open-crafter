package mod.kelvinlby.opencrafter.client.interaction.debug;

import mod.kelvinlby.opencrafter.OpenCrafter;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;

/**
 * Dev-only test driver that acts the way another mod would, never through the crosshair or the
 * keyboard: enabled with {@code -Dopencrafter.interaction.debugModActions=N}, every N ticks it runs
 * the next of four actions on blocks diagonally ahead of the player (in view, off the crosshair) or the nearest mob:
 * a game-mode placement, a game-mode attack, a raw placement packet, a game-mode break.
 */
public final class ModActionDriver {
	private static final int SETTLE_TICKS = 100;
	private static final int SIDE_BLOCKS = 2;
	private static final double ATTACK_RANGE = 6.0;

	private final int period;
	private int ticks;
	private int step;

	private ModActionDriver(int period) {
		this.period = period;
	}

	public static void installIfRequested() {
		int period = Integer.getInteger("opencrafter.interaction.debugModActions", 0);
		if (period > 0) {
			ModActionDriver driver = new ModActionDriver(period);
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
		if (ticks > SETTLE_TICKS && ticks % period == 0) {
			runStep(minecraft, player, gameMode, step++ % 4);
		}
	}

	private static void runStep(Minecraft minecraft, LocalPlayer player, MultiPlayerGameMode gameMode, int which) {
		switch (which) {
			case 0 -> gameMode.useItemOn(player, InteractionHand.MAIN_HAND, groundHit(player, 1));
			case 1 -> attackNearest(minecraft, player, gameMode);
			case 2 -> player.connection.send(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, groundHit(player, -1), 0));
			default -> gameMode.startDestroyBlock(sideBlock(player, 1).above(), Direction.UP);
		}
	}

	private static void attackNearest(Minecraft minecraft, LocalPlayer player, MultiPlayerGameMode gameMode) {
		Entity target = nearestMob(minecraft, player);
		if (target != null) {
			gameMode.attack(player, target);
		}
	}

	private static @Nullable Entity nearestMob(Minecraft minecraft, LocalPlayer player) {
		if (minecraft.level == null) {
			return null;
		}
		return minecraft.level.getEntities(player, player.getBoundingBox().inflate(ATTACK_RANGE), entity -> entity instanceof LivingEntity)
			.stream().min(Comparator.comparingDouble(player::distanceToSqr)).orElse(null);
	}

	/** The top face of the ground block {@link #SIDE_BLOCKS} ahead and to the left (side 1) or right (side -1). */
	private static BlockHitResult groundHit(LocalPlayer player, int side) {
		BlockPos ground = sideBlock(player, side);
		return new BlockHitResult(Vec3.atCenterOf(ground).add(0.0, 0.5, 0.0), Direction.UP, ground, false);
	}

	private static BlockPos sideBlock(LocalPlayer player, int side) {
		Direction ahead = player.getDirection();
		return player.blockPosition().below().relative(ahead, SIDE_BLOCKS).relative(ahead.getCounterClockWise(), SIDE_BLOCKS * side);
	}
}
