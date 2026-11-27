package mod.kelvinlby.opencrafter.encoder.client;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerAbilitiesPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Moves the local player along the recorded path. The player's own tick still runs (pose, sneaking,
 * sprinting, swimming, item-use slowdown, walk distance and bobbing all come from vanilla code fed
 * the recorded keys), but its movement is steered so that each tick ends exactly where the
 * recorded client said it was. The camera's rotation is interpolated per frame between the
 * rotations the recorded client sent at its tick ends.
 */
final class MotionReplayer {
	private final ReplayInput input = new ReplayInput();
	private final List<Packet<?>> pendingPackets = new ArrayList<>();
	private @Nullable Vec3 target;
	private boolean steerNextMove;
	private float yRot;
	private float xRot;
	private float appliedYRot;
	private float appliedXRot;
	private float previousYRot;
	private float previousXRot;
	private boolean rotationKnown;
	private boolean onGround;
	private boolean horizontalCollision;
	private @Nullable Boolean sprinting;

	/** Takes the motion part of one recorded tick (several, if the recording caught up after lag). */
	void accept(Packet<?> packet) {
		pendingPackets.add(packet);
		switch (packet) {
			case ServerboundMovePlayerPacket move -> acceptMove(move);
			case ServerboundPlayerInputPacket in -> input.set(in.input());
			case ServerboundPlayerCommandPacket command -> acceptCommand(command);
			default -> {
			}
		}
	}

	/** Start of a client tick: the player gets the recorded keys and this tick's rotation. */
	void beforeTick(LocalPlayer player) {
		if (player.input != input) {
			player.input = input;
		}
		if (rotationKnown) {
			previousYRot = appliedYRot;
			previousXRot = appliedXRot;
			appliedYRot = yRot;
			appliedXRot = xRot;
			player.setYRot(yRot);
			player.setXRot(xRot);
		}
		steerNextMove = target != null;
	}

	/** The player's first self-move this tick goes wherever the recording ends the tick. */
	Vec3 steer(LocalPlayer player, Vec3 delta) {
		if (!steerNextMove || target == null) {
			return delta;
		}
		steerNextMove = false;
		return target.subtract(player.position());
	}

	/**
	 * End of the player's tick (where it would send its movement): pins the recorded position and
	 * state, then sends the recorded motion packets so other mods see what the server saw.
	 */
	void afterTick(LocalPlayer player) {
		if (target != null) {
			Vec3 before = new Vec3(player.xo, player.yo, player.zo);
			player.setPos(target);
			player.setDeltaMovement(target.subtract(before));
			player.setOnGround(onGround);
			player.horizontalCollision = horizontalCollision;
		}
		if (sprinting != null) {
			player.setSprinting(sprinting);
		}
		for (Packet<?> packet : pendingPackets) {
			applyAbilities(player, packet);
			player.connection.send(packet);
		}
		pendingPackets.clear();
	}

	/** Every frame before rendering: the camera turns smoothly from the last tick's rotation to this tick's. */
	void beforeFrame(LocalPlayer player, float partialTick) {
		if (!rotationKnown) {
			return;
		}
		player.setYRot(Mth.rotLerp(partialTick, previousYRot, appliedYRot));
		player.setXRot(Mth.lerp(partialTick, previousXRot, appliedXRot));
	}

	/** After a respawn or dimension change the server places the player; recorded state starts over. */
	void reset() {
		target = null;
		rotationKnown = false;
		sprinting = null;
		pendingPackets.clear();
	}

	private void acceptMove(ServerboundMovePlayerPacket move) {
		if (move.hasPosition()) {
			target = new Vec3(move.getX(0), move.getY(0), move.getZ(0));
		}
		if (move.hasRotation()) {
			yRot = move.getYRot(yRot);
			xRot = move.getXRot(xRot);
			if (!rotationKnown) {
				appliedYRot = yRot;
				appliedXRot = xRot;
				rotationKnown = true;
			}
		}
		onGround = move.isOnGround();
		horizontalCollision = move.horizontalCollision();
	}

	private void acceptCommand(ServerboundPlayerCommandPacket command) {
		switch (command.getAction()) {
			case START_SPRINTING -> sprinting = true;
			case STOP_SPRINTING -> sprinting = false;
			default -> {
			}
		}
	}

	private static void applyAbilities(LocalPlayer player, Packet<?> packet) {
		if (packet instanceof ServerboundPlayerAbilitiesPacket abilities) {
			player.getAbilities().flying = abilities.isFlying();
		} else if (packet instanceof ServerboundPlayerCommandPacket command
			&& command.getAction() == ServerboundPlayerCommandPacket.Action.START_FALL_FLYING && !player.isFallFlying()) {
			player.startFallFlying();
		}
	}
}
