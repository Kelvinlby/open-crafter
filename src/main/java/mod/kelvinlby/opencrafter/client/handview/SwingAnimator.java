package mod.kelvinlby.opencrafter.client.handview;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import mod.kelvinlby.opencrafter.client.handview.anim.RightClickPace;
import mod.kelvinlby.opencrafter.core.config.handview.SwingConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.jspecify.annotations.Nullable;

/**
 * Plays the custom left and right click swings that {@link HandActions} picks for each action,
 * and overrides the swing progress and the hand's pose while they render.
 */
final class SwingAnimator {
	/** The shake axis leans this far off vertical, towards the outside of the hand. */
	private static final double SHAKE_LEAN_RADIANS = Math.toRadians(20);
	/** A neutral swing lasts this share of a swing. */
	private static final double NEUTRAL_SHARE = 0.5;

	private final HandAnimations[] hands = {new HandAnimations(), new HandAnimations()};
	private long ticks;

	/** Start of every client tick; {@code breaking}: a break runs, which holds the stub like a held attack key. */
	void tick(Minecraft minecraft, @Nullable SwingConfig config, boolean breaking) {
		ticks++;
		boolean attackHeld = minecraft.options.keyAttack.isDown() || breaking;
		boolean useHeld = minecraft.options.keyUse.isDown();
		for (InteractionHand hand : InteractionHand.values()) {
			if (config == null) {
				of(hand).playVanilla();
			} else {
				of(hand).update(ticks, attackHeld, useHeld, vanillaSwinging(minecraft.player, hand));
			}
		}
	}

	void playStub(InteractionHand hand) {
		of(hand).playStub(ticks);
	}

	void playShake(InteractionHand hand) {
		of(hand).playShake(ticks);
	}

	/** A block was placed: its item flew off ({@code flies}, the hand holds still) or it was flat (a small jab). */
	void playPlaced(InteractionHand hand, boolean flies, int swingTicks) {
		if (flies) {
			of(hand).playMorph();
		} else {
			of(hand).playJab(ticks, RightClickPace.ticks(swingTicks));
		}
	}

	/** A swing no action asked for; it takes over only an idle hand or one in vanilla's swing. */
	void playNeutral(InteractionHand hand, int swingTicks) {
		HandAnimations animations = of(hand);
		if (animations.owner() == HandAnimations.Owner.VANILLA) {
			animations.playNeutral(ticks, swingTicks * NEUTRAL_SHARE);
		}
	}

	void playVanilla(InteractionHand hand) {
		of(hand).playVanilla();
	}

	/** The swing progress {@code hand} shows: the stub's, the jab's or the neutral one's, none while shaking or morphing, else vanilla's. */
	float attack(InteractionHand hand, float vanilla, float partialTicks) {
		HandAnimations animations = of(hand);
		double now = ticks + partialTicks;
		return switch (animations.owner()) {
			case VANILLA -> vanilla;
			case STUB -> animations.stub.progress(now);
			case JAB -> HandAnimations.JAB_PROGRESS * animations.outAndBack.weight(now);
			case NEUTRAL -> HandAnimations.NEUTRAL_PROGRESS * animations.outAndBack.weight(now);
			case SHAKE, MORPH -> 0;
		};
	}

	/** True while a custom swing drives {@code hand}: it renders as a swing even after vanilla's swing ended. */
	boolean forcesSwing(InteractionHand hand) {
		HandAnimations.Owner owner = of(hand).owner();
		return owner == HandAnimations.Owner.STUB || owner == HandAnimations.Owner.JAB || owner == HandAnimations.Owner.NEUTRAL;
	}

	/** True while {@code hand} plays any swing, custom or vanilla's. */
	boolean playing(InteractionHand hand, @Nullable LocalPlayer player) {
		return of(hand).owner() != HandAnimations.Owner.VANILLA || vanillaSwinging(player, hand);
	}

	/** Moves the pose for a shake; call before any other hand transform. */
	void applyMotion(PoseStack pose, InteractionHand hand, HumanoidArm arm, float partialTicks) {
		HandAnimations animations = of(hand);
		if (animations.owner() == HandAnimations.Owner.SHAKE) {
			applyShake(pose, animations, arm, ticks + partialTicks);
		}
	}

	private static void applyShake(PoseStack pose, HandAnimations animations, HumanoidArm arm, double now) {
		float offset = animations.shake.offset(now);
		float outward = arm == HumanoidArm.RIGHT ? 1 : -1;
		pose.translate(outward * offset * (float) Math.sin(SHAKE_LEAN_RADIANS), offset * (float) Math.cos(SHAKE_LEAN_RADIANS), 0);
		pose.rotateDegrees(Axis.XP, animations.shake.tilt(now));
	}

	private static boolean vanillaSwinging(@Nullable LocalPlayer player, InteractionHand hand) {
		LivingEntity.SwingDescription swing = player == null ? null : player.getCurrentSwing();
		return swing != null && swing.hand() == hand;
	}

	private HandAnimations of(InteractionHand hand) {
		return hands[hand.ordinal()];
	}
}
