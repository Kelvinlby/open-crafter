package mod.kelvinlby.opencrafter.client.handview;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import mod.kelvinlby.opencrafter.core.config.handview.SwingConfig;
import mod.kelvinlby.opencrafter.mixin.handview.HandItemsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Plays the custom left and right click swings: it learns what caused each swing of the local
 * player and then overrides the swing progress and the hand's pose while it renders.
 */
final class SwingAnimator {
	/** What a right click did. */
	enum RightClick {
		/** Placed a block: morph into it, or jab. */
		PLACE,
		/** Used an entity or block: shake while held. */
		INTERACT,
		/** Used the item itself (throwing, ...): vanilla swing. */
		USE
	}

	/** The shake axis leans this far off vertical, towards the outside of the hand. */
	private static final double SHAKE_LEAN_RADIANS = Math.toRadians(20);

	/** A morph reaches the block in this share of a swing. */
	private static final double MORPH_SHARE = 0.5;

	final HeldBackBlocks heldBack = new HeldBackBlocks();
	private final HandAnimations[] hands = {new HandAnimations(), new HandAnimations()};
	private long ticks;
	private @Nullable PlacedBlock pendingPlacement;

	/** Start of every client tick. */
	void tick(Minecraft minecraft, @Nullable SwingConfig config) {
		ticks++;
		boolean attackHeld = minecraft.options.keyAttack.isDown();
		boolean useHeld = minecraft.options.keyUse.isDown();
		for (InteractionHand hand : InteractionHand.values()) {
			if (config == null) {
				of(hand).playVanilla();
			} else {
				HandAnimations.MorphEvent event = of(hand).update(ticks, attackHeld, useHeld, vanillaSwinging(minecraft.player, hand));
				onMorphEvent(minecraft.player, hand, event);
			}
		}
		heldBack.revealAllBut(travellingBlocks());
		pendingPlacement = null;
	}

	/** Once the hand has let go of a morphed block, the next item (or the bare hand) comes up from below. */
	private static void onMorphEvent(@Nullable LocalPlayer player, InteractionHand hand, HandAnimations.MorphEvent event) {
		if (event != HandAnimations.MorphEvent.SETTLED || player == null) {
			return;
		}
		HandItemsAccessor items = (HandItemsAccessor) player.firstPersonHandsAndItems();
		if (hand == InteractionHand.MAIN_HAND) {
			items.openCrafter$setMainHandHeight(0);
			items.openCrafter$setOldMainHandHeight(0);
		} else {
			items.openCrafter$setOffHandHeight(0);
			items.openCrafter$setOldOffHandHeight(0);
		}
	}

	/** Blocks a hand is still morphing into, which stay held back. */
	private Set<BlockPos> travellingBlocks() {
		Set<BlockPos> blocks = new HashSet<>();
		for (HandAnimations hand : hands) {
			BlockMorph.Target morph = hand.travellingMorph();
			if (morph != null) {
				blocks.add(morph.pos());
			}
		}
		return blocks;
	}

	/** The stack {@code hand} draws: the placed one while it morphs into its block, else {@code shown}. */
	ItemStack shownItem(InteractionHand hand, ItemStack shown) {
		BlockMorph.Target morph = of(hand).drawnMorph();
		return morph == null ? shown : morph.item();
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
	void notePlacement(@Nullable PlacedBlock placed) {
		pendingPlacement = placed;
	}

	void onRightSwing(SwingConfig config, LocalPlayer player, RightClick kind) {
		HandAnimations hand = swungHand(player);
		PlacedBlock placed = pendingPlacement;
		pendingPlacement = null;
		if (config.rightClick != SwingConfig.RightClickAnimation.SMOOTH || kind == RightClick.USE) {
			hand.playVanilla();
		} else if (kind == RightClick.PLACE && placed != null) {
			playPlacement(hand, player, placed);
		} else {
			hand.playShake(ticks);
		}
	}

	/** A placed block: a 3D block item turns into it, a flat item jabs. */
	private void playPlacement(HandAnimations hand, LocalPlayer player, PlacedBlock placed) {
		ClientLevel level = Minecraft.getInstance().level;
		HumanoidArm arm = swungArm(player);
		BlockMorph.Target morph = level == null ? null : BlockMorph.resolve(ModelFaces.heldModel(player, placed.held(), arm), placed, level);
		if (morph != null && level != null) {
			hand.playMorph(ticks, morph, swingTicks(player) * MORPH_SHARE);
			heldBack.holdBack(level, player, placed);
		} else {
			hand.playJab(ticks, swingTicks(player));
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

	/** The morph into a placed block; call last, right before the item is drawn. */
	void applyMorph(PoseStack pose, InteractionHand hand, ItemStackRenderState item, HandFrame frame, float partialTicks) {
		HandAnimations animations = of(hand);
		BlockMorph.Target morph = animations.drawnMorph();
		float weight = animations.travel.weight(ticks + partialTicks);
		if (morph != null && weight > 0) {
			BlockMorph.apply(pose, item, morph, frame, weight);
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

	private static HumanoidArm swungArm(LocalPlayer player) {
		LivingEntity.SwingDescription swing = player.getCurrentSwing();
		boolean main = swing == null || swing.hand() == InteractionHand.MAIN_HAND;
		return main ? player.getMainArm() : player.getMainArm().getOpposite();
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
