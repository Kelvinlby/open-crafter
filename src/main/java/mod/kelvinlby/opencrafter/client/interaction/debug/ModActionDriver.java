package mod.kelvinlby.opencrafter.client.interaction.debug;

import mod.kelvinlby.opencrafter.OpenCrafter;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
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
 * {@code -Dopencrafter.interaction.debugModSteps=a,b,...} picks the cycle from those ({@code place},
 * {@code attack}, {@code rawplace}, {@code break}) and two more: {@code rawbreak} (raw start and,
 * once the held tool has broken it, raw stop on the block ahead-left, as a survival mining mod does)
 * and {@code use} (a game-mode use of the block ahead-left: a door or lever placed there toggles).
 */
public final class ModActionDriver {
	private static final int SETTLE_TICKS = 100;
	private static final int SIDE_BLOCKS = 2;
	private static final double ATTACK_RANGE = 6.0;

	private static final String DEFAULT_STEPS = "place,attack,rawplace,break";

	private final int period;
	private final String[] steps;
	private int ticks;
	private int step;
	private @Nullable BlockPos breaking;
	private int stopAt;

	private ModActionDriver(int period, String[] steps) {
		this.period = period;
		this.steps = steps;
	}

	public static void installIfRequested() {
		int period = Integer.getInteger("opencrafter.interaction.debugModActions", 0);
		if (period > 0) {
			ModActionDriver driver = new ModActionDriver(period, System.getProperty("opencrafter.interaction.debugModSteps", DEFAULT_STEPS).split(","));
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
		finishRawBreak(player);
		if (ticks > SETTLE_TICKS && ticks % period == 0) {
			runStep(minecraft, player, gameMode, steps[step++ % steps.length].trim());
		}
	}

	private void runStep(Minecraft minecraft, LocalPlayer player, MultiPlayerGameMode gameMode, String which) {
		switch (which) {
			case "place" -> gameMode.useItemOn(player, InteractionHand.MAIN_HAND, groundHit(player, 1));
			case "attack" -> attackNearest(minecraft, player, gameMode);
			case "rawplace" -> player.connection.send(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, groundHit(player, -1), 0));
			case "rawbreak" -> startRawBreak(minecraft, player);
			case "use" -> gameMode.useItemOn(player, InteractionHand.MAIN_HAND, facingHit(player, sideBlock(player, 1).above()));
			default -> gameMode.startDestroyBlock(sideBlock(player, 1).above(), Direction.UP);
		}
	}

	/** Raw start-destroy now; the stop follows once the held tool has broken the block (see {@link #finishRawBreak}). */
	private void startRawBreak(Minecraft minecraft, LocalPlayer player) {
		BlockPos pos = sideBlock(player, 1).above();
		float perTick = minecraft.level == null ? 0 : minecraft.level.getBlockState(pos).getDestroyProgress(player, minecraft.level, pos);
		if (perTick <= 0) {
			return;
		}
		player.connection.send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, Direction.UP, 0));
		breaking = pos;
		stopAt = ticks + (int) Math.ceil(1.0F / perTick);
	}

	private void finishRawBreak(LocalPlayer player) {
		if (breaking != null && ticks >= stopAt) {
			player.connection.send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, breaking, Direction.UP, 0));
			breaking = null;
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

	/** The centre of the face of {@code pos} that looks at the player. */
	private static BlockHitResult facingHit(LocalPlayer player, BlockPos pos) {
		Direction face = player.getDirection().getOpposite();
		return new BlockHitResult(Vec3.atCenterOf(pos).add(Vec3.atLowerCornerOf(face.getUnitVec3i()).scale(0.5)), face, pos, false);
	}

	private static BlockPos sideBlock(LocalPlayer player, int side) {
		Direction ahead = player.getDirection();
		return player.blockPosition().below().relative(ahead, SIDE_BLOCKS).relative(ahead.getCounterClockWise(), SIDE_BLOCKS * side);
	}
}
