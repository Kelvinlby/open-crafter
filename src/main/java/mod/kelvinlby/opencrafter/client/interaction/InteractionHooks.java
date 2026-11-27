package mod.kelvinlby.opencrafter.client.interaction;

import mod.kelvinlby.opencrafter.client.interaction.capture.InteractionCapture;
import mod.kelvinlby.opencrafter.client.interaction.outcome.OutcomeResolver;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

import java.util.List;

/** Entry points for the interaction mixins; each forwards to the capture or the outcome resolver. */
public final class InteractionHooks {
	private static @Nullable InteractionCapture capture;
	private static @Nullable OutcomeResolver outcomes;

	private InteractionHooks() {
	}

	static void install(InteractionCapture installedCapture, OutcomeResolver installedOutcomes) {
		capture = installedCapture;
		outcomes = installedOutcomes;
	}

	/** Any packet the client sends; off-thread sends (some mods) are handled on the client thread, in order. */
	public static void onPacketSent(Packet<?> packet) {
		if (capture == null || !isServerbound(packet)) {
			return;
		}
		Minecraft minecraft = Minecraft.getInstance();
		if (minecraft.isSameThread()) {
			capture.onPacketSent(packet);
		} else {
			minecraft.execute(() -> capture.onPacketSent(packet));
		}
	}

	public static void enterUseItemOn(BlockHitResult hit) {
		enter(List.of(hit.getBlockPos(), hit.getBlockPos().relative(hit.getDirection())));
	}

	public static void enterBreak(BlockPos pos) {
		enter(List.of(pos));
	}

	/** An action without blocks to watch (attack, interact, use, stab, release, stop breaking). */
	public static void enterOther() {
		enter(List.of());
	}

	public static void exitGameMode() {
		if (capture != null && Minecraft.getInstance().isSameThread()) {
			capture.exitGameMode();
		}
	}

	/** The server reported an entity hurt (damage event or hurt animation). */
	public static void onEntityHurt(int entityId) {
		if (outcomes != null) {
			outcomes.onEntityHurt(entityId);
		}
	}

	private static void enter(List<BlockPos> watched) {
		if (capture != null && Minecraft.getInstance().isSameThread()) {
			capture.enterGameMode(watched);
		}
	}

	private static boolean isServerbound(Packet<?> packet) {
		return packet.type().flow() == PacketFlow.SERVERBOUND;
	}
}
