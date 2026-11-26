package mod.kelvinlby.opencrafter.client.handview;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import mod.kelvinlby.opencrafter.client.handview.anim.RightClickPace;
import mod.kelvinlby.opencrafter.core.config.handview.SwingConfig;
import mod.kelvinlby.opencrafter.mixin.handview.HandItemsAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
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

	/** A flight reaches its block in this share of a swing, at the right click pace. */
	private static final double FLIGHT_SHARE = 0.5;
	/** A hand pose older than this (ticks) is stale: the hand was not drawn holding an item since. */
	private static final long HAND_POSE_MAX_AGE = 1;

	final HeldBackBlocks heldBack = new HeldBackBlocks();
	private final HandAnimations[] hands = {new HandAnimations(), new HandAnimations()};
	private final List<MorphFlight> flights = new ArrayList<>();
	/** Each hand's last drawn pose in camera space and the tick it was drawn on: where a flight leaves from. */
	private final Matrix4f[] handPoses = {new Matrix4f(), new Matrix4f()};
	private final long[] handPoseTicks = {Long.MIN_VALUE, Long.MIN_VALUE};
	private long ticks;
	private HandFrame lastFrame = new HandFrame();
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
				of(hand).update(ticks, attackHeld, useHeld, vanillaSwinging(minecraft.player, hand));
			}
		}
		tickFlights(config != null);
		pendingPlacement = null;
	}

	private void tickFlights(boolean enabled) {
		if (!enabled) {
			flights.clear();
		}
		for (MorphFlight flight : flights) {
			flight.tick(ticks);
		}
		flights.removeIf(MorphFlight::done);
		heldBack.revealAllBut(travellingBlocks());
	}

	/** The item left the hand: the next one (or the bare hand) comes up from below. */
	private static void dropHand(LocalPlayer player, InteractionHand hand) {
		HandItemsAccessor items = (HandItemsAccessor) player.firstPersonHandsAndItems();
		if (hand == InteractionHand.MAIN_HAND) {
			items.openCrafter$setMainHandHeight(0);
			items.openCrafter$setOldMainHandHeight(0);
		} else {
			items.openCrafter$setOffHandHeight(0);
			items.openCrafter$setOldOffHandHeight(0);
		}
	}

	/** Blocks a flight is still on its way to, which stay held back. */
	private Set<BlockPos> travellingBlocks() {
		Set<BlockPos> blocks = new HashSet<>();
		for (MorphFlight flight : flights) {
			if (flight.travelling()) {
				blocks.add(flight.target.pos());
			}
		}
		return blocks;
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
			playPlacement(swungHandId(player), player, placed);
		} else {
			hand.playShake(ticks);
		}
	}

	/** A placed block: a 3D block item flies off and turns into it, a flat item jabs. */
	private void playPlacement(InteractionHand hand, LocalPlayer player, PlacedBlock placed) {
		ClientLevel level = Minecraft.getInstance().level;
		HumanoidArm arm = armOf(player, hand);
		ItemStackRenderState model = ModelFaces.heldModel(player, placed.held(), arm);
		BlockMorph.Target morph = level == null ? null : BlockMorph.resolve(model, placed, level);
		if (morph == null || level == null) {
			of(hand).playJab(ticks, RightClickPace.ticks(swingTicks(player)));
			return;
		}
		flights.add(new MorphFlight(morph, model, launchPose(hand, arm), ticks, RightClickPace.ticks(swingTicks(player) * FLIGHT_SHARE)));
		heldBack.holdBack(level, placed);
		of(hand).playMorph();
		dropHand(player, hand);
	}

	/** Where a flight leaves {@code hand}: its last drawn pose, or the resting place when it held nothing. */
	private Matrix4f launchPose(InteractionHand hand, HumanoidArm arm) {
		int index = hand.ordinal();
		boolean fresh = ticks - handPoseTicks[index] <= HAND_POSE_MAX_AGE;
		return fresh ? new Matrix4f(handPoses[index]) : BlockMorph.restCameraPose(lastFrame, arm);
	}

	/** Right before a held item is drawn: its pose, for flights launched from this hand. */
	void noteHandPose(InteractionHand hand, HandFrame frame, PoseStack pose) {
		lastFrame = frame;
		handPoses[hand.ordinal()].set(BlockMorph.cameraPose(frame, pose.last().pose()));
		handPoseTicks[hand.ordinal()] = ticks;
	}

	/** Draws every flight; call at the end of the hand pass. */
	void drawFlights(PoseStack pose, SubmitNodeCollector collector, int light, HandFrame frame, float partialTicks) {
		lastFrame = frame;
		for (MorphFlight flight : flights) {
			BlockMorph.draw(pose, collector, light, flight, frame, flight.weight(ticks + partialTicks));
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

	private static HumanoidArm armOf(LocalPlayer player, InteractionHand hand) {
		return hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
	}

	private static InteractionHand swungHandId(LivingEntity player) {
		LivingEntity.SwingDescription swing = player.getCurrentSwing();
		return swing == null ? InteractionHand.MAIN_HAND : swing.hand();
	}

	private HandAnimations swungHand(LivingEntity player) {
		return of(swungHandId(player));
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
