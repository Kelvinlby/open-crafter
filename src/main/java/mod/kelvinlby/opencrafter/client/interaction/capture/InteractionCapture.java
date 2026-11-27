package mod.kelvinlby.opencrafter.client.interaction.capture;

import mod.kelvinlby.opencrafter.client.interaction.event.InteractionBus;
import mod.kelvinlby.opencrafter.client.interaction.event.InteractionEvent;
import mod.kelvinlby.opencrafter.client.interaction.outcome.OutcomeResolver;
import mod.kelvinlby.opencrafter.client.interaction.outcome.PreAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

import java.util.List;

/**
 * Turns every outgoing in-world action into an {@link InteractionEvent}: packets are the source of
 * truth, {@code MultiPlayerGameMode} calls add the world as it was before the client predicted the
 * result. Client thread only.
 */
public final class InteractionCapture {
	private final InteractionBus bus;
	private final OutcomeResolver outcomes;
	private final SentRotation rotation;
	private final FrameClock frames;
	private final GameModeScope scope = new GameModeScope();
	private long nextId = 1L;

	public InteractionCapture(InteractionBus bus, OutcomeResolver outcomes, SentRotation rotation, FrameClock frames) {
		this.bus = bus;
		this.outcomes = outcomes;
		this.rotation = rotation;
		this.frames = frames;
	}

	/** A {@code MultiPlayerGameMode} action starts; {@code watched} are the blocks it may change. */
	public void enterGameMode(List<BlockPos> watched) {
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.player != null && minecraft.level != null) {
			scope.enter(PreAction.capture(minecraft, minecraft.player, minecraft.level, watched));
		}
	}

	/** The action's call returned: its client-side prediction is in the world now. */
	public void exitGameMode() {
		scope.exit();
		outcomes.checkNow();
	}

	public void onPacketSent(Packet<?> packet) {
		if (packet instanceof ServerboundMovePlayerPacket move) {
			rotation.onMovePacket(move);
			return;
		}
		SentAction action = PacketReader.read(packet);
		Minecraft minecraft = Minecraft.getInstance();
		if (action != null && minecraft.player != null && minecraft.level != null) {
			emit(action, minecraft, minecraft.player, minecraft.level);
		}
	}

	/** Joining or leaving a world. */
	public void reset() {
		scope.clear();
		rotation.reset();
		outcomes.clear();
	}

	private void emit(SentAction action, Minecraft minecraft, LocalPlayer player, ClientLevel level) {
		PreAction inGameMode = scope.current();
		PreAction before = inGameMode != null ? inGameMode : PreAction.capture(minecraft, player, level, PreAction.watchedFor(action.target()));
		InteractionEvent event = EventBuilder.build(nextId++, frames.lastCaptured(), action, before, rotation.current(player),
			inGameMode != null, minecraft, player, level);
		bus.publish(event);
		outcomes.track(event, before, player);
		outcomes.checkNow();
	}
}
