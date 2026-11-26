package mod.kelvinlby.opencrafter.client.handview;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import mod.kelvinlby.opencrafter.mixin.handview.LocalPlayerAccessor;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;

/**
 * The rotation last sent to the server. Vanilla always sends the player's own; a mod that spoofs
 * it (rotation modules) leaves them apart after the tick, and then the hands can follow the sent one.
 */
final class ServerRotation {
	private static final float EPSILON_DEGREES = 1.0E-3F;

	private boolean spoofed;

	/** End of every client tick, after the movement packet went out. */
	void tick(LocalPlayer player) {
		LocalPlayerAccessor sent = (LocalPlayerAccessor) player;
		spoofed = Math.abs(Mth.wrapDegrees(sent.openCrafter$yRotLast() - player.getYRot())) > EPSILON_DEGREES
			|| Math.abs(sent.openCrafter$xRotLast() - player.getXRot()) > EPSILON_DEGREES;
	}

	void reset() {
		spoofed = false;
	}

	/** Turns the hand from the player's view to the sent rotation while one is spoofed. */
	void apply(PoseStack pose, LocalPlayer player) {
		if (!spoofed) {
			return;
		}
		LocalPlayerAccessor sent = (LocalPlayerAccessor) player;
		pose.rotateDegrees(Axis.XP, player.getXRot() - sent.openCrafter$xRotLast());
		pose.rotateDegrees(Axis.YP, player.getYRot() - sent.openCrafter$yRotLast());
	}
}
