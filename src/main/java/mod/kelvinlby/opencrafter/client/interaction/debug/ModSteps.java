package mod.kelvinlby.opencrafter.client.interaction.debug;

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
 * The driver's actions, by name: {@code place} (game-mode placement ahead-left), {@code attack}
 * (game-mode attack on the nearest mob), {@code rawplace} (raw placement packet ahead-right),
 * {@code break} (game-mode break of the block above ahead-left), {@code rawbreak} (raw start and,
 * once the held tool has broken it, raw stop, as a survival mining mod does) and {@code use}
 * (game-mode use of the block above ahead-left: a door or lever there toggles).
 */
final class ModSteps {
	private static final int SIDE_BLOCKS = 2;
	private static final double ATTACK_RANGE = 6.0;

	private @Nullable BlockPos breaking;
	private int stopAt;

	void run(Minecraft minecraft, LocalPlayer player, MultiPlayerGameMode gameMode, String step, int ticks) {
		switch (step) {
			case "place" -> gameMode.useItemOn(player, InteractionHand.MAIN_HAND, groundHit(player, 1));
			case "attack" -> attackNearest(minecraft, player, gameMode);
			case "rawplace" -> player.connection.send(new ServerboundUseItemOnPacket(InteractionHand.MAIN_HAND, groundHit(player, -1), 0));
			case "rawbreak" -> startRawBreak(minecraft, player, ticks);
			case "use" -> gameMode.useItemOn(player, InteractionHand.MAIN_HAND, facingHit(player, aheadLeftAbove(player)));
			default -> gameMode.startDestroyBlock(aheadLeftAbove(player), Direction.UP);
		}
	}

	/** Every tick: sends a raw break's stop once its time is up. */
	void tick(LocalPlayer player, int ticks) {
		if (breaking != null && ticks >= stopAt) {
			player.connection.send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, breaking, Direction.UP, 0));
			breaking = null;
		}
	}

	/** The world point a step acts on, or null when it has none (no mob in range). */
	@Nullable Vec3 target(Minecraft minecraft, LocalPlayer player, String step) {
		return switch (step) {
			case "place" -> groundHit(player, 1).getLocation();
			case "attack" -> {
				Entity mob = nearestMob(minecraft, player);
				yield mob == null ? null : mob.getBoundingBox().getCenter();
			}
			case "rawplace" -> groundHit(player, -1).getLocation();
			default -> Vec3.atCenterOf(aheadLeftAbove(player));
		};
	}

	/** Raw start-destroy now; the stop follows once the held tool has broken the block (see {@link #tick}). */
	private void startRawBreak(Minecraft minecraft, LocalPlayer player, int ticks) {
		BlockPos pos = aheadLeftAbove(player);
		float perTick = minecraft.level == null ? 0 : minecraft.level.getBlockState(pos).getDestroyProgress(player, minecraft.level, pos);
		if (perTick <= 0) {
			return;
		}
		player.connection.send(new ServerboundPlayerActionPacket(ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK, pos, Direction.UP, 0));
		breaking = pos;
		stopAt = ticks + (int) Math.ceil(1.0F / perTick);
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

	private static BlockPos aheadLeftAbove(LocalPlayer player) {
		return sideBlock(player, 1).above();
	}

	private static BlockPos sideBlock(LocalPlayer player, int side) {
		Direction ahead = player.getDirection();
		return player.blockPosition().below().relative(ahead, SIDE_BLOCKS).relative(ahead.getCounterClockWise(), SIDE_BLOCKS * side);
	}
}
