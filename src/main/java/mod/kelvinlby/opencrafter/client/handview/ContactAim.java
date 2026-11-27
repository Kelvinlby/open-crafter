package mod.kelvinlby.opencrafter.client.handview;

import com.mojang.blaze3d.vertex.PoseStack;
import mod.kelvinlby.opencrafter.client.handview.anim.AimTurn;
import mod.kelvinlby.opencrafter.client.handview.anim.Envelope;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

/**
 * Turns each hand toward the contact point of the action it plays when that action was off the
 * crosshair (another mod's, or a rotation mod's). The turn eases in, holds while the action's
 * animation plays and eases out; actions on the crosshair leave the hand as it is.
 */
final class ContactAim {
	private static final double RISE_TICKS = 1.5;
	private static final double FALL_TICKS = 3;
	private static final Vector3fc EYE = new Vector3f();

	private final Envelope[] levels = {new Envelope(RISE_TICKS, FALL_TICKS), new Envelope(RISE_TICKS, FALL_TICKS)};
	private final @Nullable Vec3[] contacts = new Vec3[2];

	/** {@code hand} starts an action aimed at {@code contact} (world space). */
	void engage(InteractionHand hand, Vec3 contact, double now) {
		contacts[hand.ordinal()] = contact;
		levels[hand.ordinal()].engage(now);
	}

	/** Once per tick: the turn holds while {@code playing}, else eases out. */
	void update(InteractionHand hand, double now, boolean playing) {
		levels[hand.ordinal()].update(now, playing);
	}

	/** How far the turn is in, in [0, 1]. */
	float level(InteractionHand hand, double now) {
		return (float) levels[hand.ordinal()].value(now);
	}

	void reset() {
		for (Envelope level : levels) {
			level.reset();
		}
	}

	/**
	 * Turns {@code pose} (a hand's, as its pass starts) about the eye toward its contact: the hand
	 * plays as if the view looked at the contact, so its whole swing heads there.
	 */
	void apply(PoseStack pose, HandFrame frame, InteractionHand hand, double now) {
		Vec3 contact = contacts[hand.ordinal()];
		float level = level(hand, now);
		if (contact == null || level <= 0) {
			return;
		}
		Quaternionf turn = AimTurn.toward(EYE, HandSpace.inHandSpace(contact), level);
		Matrix4f toCamera = frame.modelView().mul(pose.last().pose());
		Matrix4f turned = new Matrix4f().rotation(turn).mul(toCamera);
		pose.mulPose(toCamera.invert().mul(turned));
	}
}
