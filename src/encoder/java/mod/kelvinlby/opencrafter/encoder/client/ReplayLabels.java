package mod.kelvinlby.opencrafter.encoder.client;

import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.entity.player.Input;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The recorded client's input, gathered between two captured frames: what a policy trained on the
 * frames would have to output. Movement keys and the camera are the state at the last tick before
 * the frame; discrete actions are every one sent since the previous frame, in order.
 */
final class ReplayLabels {
	private final List<Map<String, Object>> events = new ArrayList<>();
	private Input input = Input.EMPTY;
	private float yaw;
	private float pitch;
	private int hotbar = -1;
	private int ticks;
	private long recordedTicks;

	/** {@code recorded} ticks of the recorded client were applied in one replay tick, with these packets. */
	void onTick(int recorded, List<Packet<?>> packets) {
		ticks += recorded;
		recordedTicks += recorded;
		packets.forEach(this::onPacket);
	}

	/** The labels for the frame being captured; starts the next frame's collection. */
	Map<String, Object> takeForFrame(long recordingMicros) {
		Map<String, Object> labels = new LinkedHashMap<>();
		labels.put("recording_micros", recordingMicros);
		labels.put("recorded_ticks", recordedTicks);
		labels.put("ticks_since_last_frame", ticks);
		labels.put("keys", keys(input));
		labels.put("yaw", yaw);
		labels.put("pitch", pitch);
		labels.put("hotbar", hotbar);
		labels.put("events", List.copyOf(events));
		events.clear();
		ticks = 0;
		return labels;
	}

	private void onPacket(Packet<?> packet) {
		switch (packet) {
			case ServerboundPlayerInputPacket in -> input = in.input();
			case ServerboundMovePlayerPacket move when move.hasRotation() -> {
				yaw = move.getYRot(yaw);
				pitch = move.getXRot(pitch);
			}
			case ServerboundSetCarriedItemPacket carried -> {
				hotbar = carried.getSlot();
				events.add(event(packet, Map.of("slot", carried.getSlot())));
			}
			default -> {
				if (!RecordedPackets.isMotion(packet) && !RecordedPackets.isBookkeeping(packet)) {
					events.add(describe(packet));
				}
			}
		}
	}

	private static Map<String, Object> describe(Packet<?> packet) {
		return switch (packet) {
			case ServerboundPlayerActionPacket action -> event(packet, Map.of("action", action.getAction().name(),
				"pos", pos(action.getPos()), "face", action.getDirection().getSerializedName()));
			case ServerboundUseItemOnPacket use -> event(packet, Map.of("hand", use.hand().name(), "pos", pos(use.hitResult().getBlockPos()),
				"face", use.hitResult().getDirection().getSerializedName(), "inside", use.hitResult().isInside()));
			case ServerboundUseItemPacket use -> event(packet, Map.of("hand", use.hand().name()));
			case ServerboundAttackPacket attack -> event(packet, Map.of("entity", attack.entityId()));
			case ServerboundInteractPacket interact -> event(packet, Map.of("entity", interact.entityId(), "hand", interact.hand().name(),
				"sneaking", interact.usingSecondaryAction()));
			case ServerboundContainerClickPacket click -> event(packet, Map.of("container", click.containerId(), "slot", (int) click.slotNum(),
				"button", (int) click.buttonNum(), "input", click.containerInput().name()));
			case ServerboundContainerClosePacket close -> event(packet, Map.of("container", close.getContainerId()));
			case ServerboundPlayerCommandPacket command -> event(packet, Map.of("action", command.getAction().name()));
			default -> event(packet, Map.of());
		};
	}

	private static Map<String, Object> event(Packet<?> packet, Map<String, Object> fields) {
		Map<String, Object> event = new LinkedHashMap<>();
		event.put("type", packet.type().id().getPath());
		event.putAll(fields);
		return event;
	}

	private static List<Integer> pos(BlockPos pos) {
		return List.of(pos.getX(), pos.getY(), pos.getZ());
	}

	private static Map<String, Boolean> keys(Input input) {
		Map<String, Boolean> keys = new LinkedHashMap<>();
		keys.put("forward", input.forward());
		keys.put("backward", input.backward());
		keys.put("left", input.left());
		keys.put("right", input.right());
		keys.put("jump", input.jump());
		keys.put("sneak", input.shift());
		keys.put("sprint", input.sprint());
		return keys;
	}
}
