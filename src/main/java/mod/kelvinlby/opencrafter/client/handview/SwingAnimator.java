package mod.kelvinlby.opencrafter.client.handview;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import mod.kelvinlby.opencrafter.core.config.handview.SwingConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

/**
 * Plays the custom left and right click swings: it learns what caused each swing of the local
 * player and then overrides the swing progress and the hand's pose while it renders.
 */
final class SwingAnimator {
	/** What a right click did. */
	enum RightClick {
		/** Placed a block: reach out to it. */
		PLACE,
		/** Used an entity or block: shake while held. */
		INTERACT,
		/** Used the item itself (throwing, ...): vanilla swing. */
		USE
	}

	/** The shake axis leans this far off vertical, towards the outside of the hand. */
	private static final double SHAKE_LEAN_RADIANS = Math.toRadians(20);

	private final HandAnimations[] hands = {new HandAnimations(), new HandAnimations()};
	private long ticks;
	private @Nullable Vec3 pendingPlacement;

	/** Start of every client tick. */
	void tick(Minecraft minecraft, @Nullable SwingConfig config) {
		ticks++;
		boolean attackHeld = minecraft.options.keyAttack.isDown();
		boolean useHeld = minecraft.options.keyUse.isDown();
		for (HandAnimations hand : hands) {
			if (config == null) {
				hand.playVanilla();
			} else {
				hand.update(ticks, attackHeld, useHeld);
			}
		}
		pendingPlacement = null;
	}

	void onLeftSwing(SwingConfig config, LocalPlayer player) {
		HandAnimations hand = swungHand(player);
		if (config.leftClick == SwingConfig.LeftClickAnimation.STUB) {
			hand.playStub(ticks);
		} else {
			hand.playVanilla();
		}
	}

	/** After a right click on a block: where a block was placed, or null. */
	void notePlacement(@Nullable Vec3 placedCentre) {
		pendingPlacement = placedCentre;
	}

	void onRightSwing(SwingConfig config, LocalPlayer player, RightClick kind) {
		HandAnimations hand = swungHand(player);
		Vec3 placed = pendingPlacement;
		pendingPlacement = null;
		if (config.rightClick != SwingConfig.RightClickAnimation.SMOOTH || kind == RightClick.USE) {
			hand.playVanilla();
		} else if (kind == RightClick.PLACE && placed != null) {
			hand.playReach(ticks, placed, swingTicks(player));
		} else {
			hand.playShake(ticks);
		}
	}

	/** The swing progress {@code hand} shows: the stub's, none while shaking or reaching, else vanilla's. */
	float attack(InteractionHand hand, float vanilla, float partialTicks) {
		HandAnimations animations = of(hand);
		return switch (animations.owner()) {
			case VANILLA -> vanilla;
			case STUB -> animations.stub.progress(ticks + partialTicks);
			case SHAKE, REACH -> 0;
		};
	}

	/** True while the stub drives {@code hand}: it renders as a swing even after vanilla's swing ended. */
	boolean forcesSwing(InteractionHand hand) {
		return of(hand).owner() == HandAnimations.Owner.STUB;
	}

	/** Moves the pose for a shake or a reach; call in camera space, before any other hand transform. */
	void applyMotion(PoseStack pose, InteractionHand hand, HumanoidArm arm, float partialTicks) {
		HandAnimations animations = of(hand);
		double now = ticks + partialTicks;
		switch (animations.owner()) {
			case SHAKE -> applyShake(pose, animations, arm, now);
			case REACH -> applyReach(pose, animations, arm, now);
			default -> {
			}
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

	private static void applyReach(PoseStack pose, HandAnimations animations, HumanoidArm arm, double now) {
		Vec3 target = animations.reachTarget();
		float weight = animations.reach.weight(now);
		if (target == null || weight <= 0) {
			return;
		}
		Vector3f delta = HandSpace.fromWorld(target).sub(HandSpace.itemRest(arm)).mul(weight);
		pose.translate(delta.x, delta.y, delta.z);
	}

	private HandAnimations swungHand(LivingEntity player) {
		LivingEntity.SwingDescription swing = player.getCurrentSwing();
		return of(swing == null ? InteractionHand.MAIN_HAND : swing.hand());
	}

	private static int swingTicks(LivingEntity player) {
		LivingEntity.SwingDescription swing = player.getCurrentSwing();
		return swing == null ? SwingConfig.VANILLA_SWING_TICKS : swing.durationTicks();
	}

	private HandAnimations of(InteractionHand hand) {
		return hands[hand.ordinal()];
	}
}
