package mod.kelvinlby.opencrafter.client.interaction.capture;

import mod.kelvinlby.opencrafter.client.interaction.event.Aim;
import mod.kelvinlby.opencrafter.client.interaction.event.InteractionKind;
import mod.kelvinlby.opencrafter.client.interaction.event.InteractionTarget;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import org.jspecify.annotations.Nullable;

/** Recognizes the serverbound packets that act on the world and reads what they say. */
final class PacketReader {
	private PacketReader() {
	}

	/** The action this packet sends, or null when it is not an in-world action. */
	static @Nullable SentAction read(Packet<?> packet) {
		return switch (packet) {
			case ServerboundAttackPacket attack -> attack(attack);
			case ServerboundInteractPacket interact -> interact(interact);
			case ServerboundUseItemOnPacket useOn -> useOn(useOn);
			case ServerboundUseItemPacket use -> use(use);
			case ServerboundPlayerActionPacket action -> playerAction(action);
			default -> null;
		};
	}

	private static SentAction attack(ServerboundAttackPacket packet) {
		return new SentAction(InteractionKind.ATTACK_ENTITY, InteractionHand.MAIN_HAND, new InteractionTarget.Entity(packet.entityId()), null, null);
	}

	private static SentAction interact(ServerboundInteractPacket packet) {
		return new SentAction(InteractionKind.INTERACT_ENTITY, packet.hand(), new InteractionTarget.Entity(packet.entityId()), packet.location(), null);
	}

	private static SentAction useOn(ServerboundUseItemOnPacket packet) {
		InteractionTarget target = new InteractionTarget.Block(packet.hitResult().getBlockPos(), packet.hitResult().getDirection());
		return new SentAction(InteractionKind.USE_ON_BLOCK, packet.hand(), target, packet.hitResult().getLocation(), null);
	}

	private static SentAction use(ServerboundUseItemPacket packet) {
		return new SentAction(InteractionKind.USE_ITEM, packet.hand(), InteractionTarget.NONE, null, new Aim(packet.yRot(), packet.xRot()));
	}

	private static @Nullable SentAction playerAction(ServerboundPlayerActionPacket packet) {
		InteractionKind kind = playerActionKind(packet.getAction());
		if (kind == null) {
			return null;
		}
		InteractionTarget target = isBreak(kind) ? new InteractionTarget.Block(packet.getPos().immutable(), packet.getDirection()) : InteractionTarget.NONE;
		return new SentAction(kind, null, target, null, null);
	}

	private static @Nullable InteractionKind playerActionKind(ServerboundPlayerActionPacket.Action action) {
		return switch (action) {
			case START_DESTROY_BLOCK -> InteractionKind.BREAK_START;
			case STOP_DESTROY_BLOCK -> InteractionKind.BREAK_DONE;
			case ABORT_DESTROY_BLOCK -> InteractionKind.BREAK_ABORT;
			case STAB -> InteractionKind.STAB;
			case RELEASE_USE_ITEM -> InteractionKind.RELEASE_USE;
			default -> null;
		};
	}

	private static boolean isBreak(InteractionKind kind) {
		return kind == InteractionKind.BREAK_START || kind == InteractionKind.BREAK_DONE || kind == InteractionKind.BREAK_ABORT;
	}
}
