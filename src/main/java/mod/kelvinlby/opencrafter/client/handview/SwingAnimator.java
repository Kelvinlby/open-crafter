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
 * Plays the custom left and right click swings: it learns what caused each swing of the local
 * player and then overrides the swing progress and the hand's pose while it renders.
 */
final class SwingAnimator {
	/** What a right click's swing was for. */
	enum RightClick {
		/** Used an entity: shake while held. */
		ENTITY,
		/** Used a block: placed one (jab, or hold still while it flies) or interacted (shake). */
		BLOCK,
		/** Used the item itself (throwing, ...): vanilla swing. */
		ITEM
	}

	/** What the current click placed, as far as the hand is concerned. */
	private enum Placement {
		NONE,
		/** A 3D block item: it flies off on its own, the hand holds still. */
		FLIES,
		/** A flat item: the hand jabs. */
		FLAT
	}

	/** The shake axis leans this far off vertical, towards the outside of the hand. */
	private static final double SHAKE_LEAN_RADIANS = Math.toRadians(20);

	private final HandAnimations[] hands = {new HandAnimations(), new HandAnimations()};
	private long ticks;
	private Placement placement = Placement.NONE;

	/** Start of every client tick. */
	void tick(Minecraft minecraft, @Nullable SwingConfig config) {
		ticks++;
		boolean attackHeld = minecraft.options.keyAttack.isDown();
		boolean useHeld = minecraft.options.keyUse.isDown();
		for (InteractionHand hand : InteractionHand.values()) {
			if (config == null) {
				of(hand).playVanilla();
			} else {
				of(hand).update(ticks, attackHeld, useHeld, vanillaSwinging(minecraft.player, hand));
			}
		}
		placement = Placement.NONE;
	}

	void onLeftSwing(SwingConfig config, LocalPlayer player) {
		HandAnimations hand = swungHand(player);
		if (config.leftClick == SwingConfig.LeftClickAnimation.STUB) {
			hand.playStub(ticks);
		} else {
			hand.playVanilla();
		}
	}

	/** The click placed a block; {@code flies}: its item flew off as a flight rather than being flat. */
	void notePlacement(boolean flies) {
		placement = flies ? Placement.FLIES : Placement.FLAT;
	}

	/** After a right click's swing played. */
	void onRightSwing(SwingConfig config, LocalPlayer player, RightClick kind) {
		HandAnimations hand = swungHand(player);
		Placement placed = placement;
		placement = Placement.NONE;
		if (config.rightClick != SwingConfig.RightClickAnimation.SMOOTH || kind == RightClick.ITEM) {
			hand.playVanilla();
		} else if (kind == RightClick.BLOCK && placed == Placement.FLIES) {
			hand.playMorph();
		} else if (kind == RightClick.BLOCK && placed == Placement.FLAT) {
			hand.playJab(ticks, RightClickPace.ticks(swingTicks(player)));
		} else {
			hand.playShake(ticks);
		}
	}

	/** The swing progress {@code hand} shows: the stub's or the jab's, none while shaking or morphing, else vanilla's. */
	float attack(InteractionHand hand, float vanilla, float partialTicks) {
		HandAnimations animations = of(hand);
		double now = ticks + partialTicks;
		return switch (animations.owner()) {
			case VANILLA -> vanilla;
			case STUB -> animations.stub.progress(now);
			case JAB -> HandAnimations.JAB_PROGRESS * animations.outAndBack.weight(now);
			case SHAKE, MORPH -> 0;
		};
	}

	/** True while the stub or the jab drives {@code hand}: it renders as a swing even after vanilla's swing ended. */
	boolean forcesSwing(InteractionHand hand) {
		HandAnimations.Owner owner = of(hand).owner();
		return owner == HandAnimations.Owner.STUB || owner == HandAnimations.Owner.JAB;
	}

	/** Moves the pose for a shake; call before any other hand transform. */
	void applyMotion(PoseStack pose, InteractionHand hand, HumanoidArm arm, float partialTicks) {
		HandAnimations animations = of(hand);
		if (animations.owner() == HandAnimations.Owner.SHAKE) {
			applyShake(pose, animations, arm, ticks + partialTicks);
		}
	}

	boolean playsCustom(InteractionHand hand) {
		return of(hand).owner() != HandAnimations.Owner.VANILLA;
	}

	private static void applyShake(PoseStack pose, HandAnimations animations, HumanoidArm arm, double now) {
		float offset = animations.shake.offset(now);
		float outward = arm == HumanoidArm.RIGHT ? 1 : -1;
		pose.translate(outward * offset * (float) Math.sin(SHAKE_LEAN_RADIANS), offset * (float) Math.cos(SHAKE_LEAN_RADIANS), 0);
		pose.rotateDegrees(Axis.XP, animations.shake.tilt(now));
	}

	private HandAnimations swungHand(LivingEntity player) {
		LivingEntity.SwingDescription swing = player.getCurrentSwing();
		return of(swing == null ? InteractionHand.MAIN_HAND : swing.hand());
	}

	private static boolean vanillaSwinging(@Nullable LocalPlayer player, InteractionHand hand) {
		LivingEntity.SwingDescription swing = player == null ? null : player.getCurrentSwing();
		return swing != null && swing.hand() == hand;
	}

	private static int swingTicks(LivingEntity player) {
		LivingEntity.SwingDescription swing = player.getCurrentSwing();
		return swing == null ? SwingConfig.VANILLA_SWING_TICKS : swing.durationTicks();
	}

	private HandAnimations of(InteractionHand hand) {
		return hands[hand.ordinal()];
	}
}
