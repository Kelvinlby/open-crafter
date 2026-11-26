package mod.kelvinlby.opencrafter.client.handview;

import com.mojang.blaze3d.vertex.PoseStack;
import mod.kelvinlby.opencrafter.client.handview.anim.EatCurve;
import mod.kelvinlby.opencrafter.core.config.handview.HandViewConfig;
import mod.kelvinlby.opencrafter.core.config.handview.SwingConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.FirstPersonHandsAndItemsRenderState;
import net.minecraft.client.renderer.state.level.PlayerRenderState;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

import java.util.function.DoubleBinaryOperator;

/**
 * Static entry points for the hand view mixins. Each returns vanilla's value until
 * {@link HandViewModule} is installed and while the global toggle is off.
 */
public final class HandViewHooks {
	private static @Nullable HandView view;
	/** Partial tick of the hands being rendered; set before every hand pass (render thread). */
	private static float partialTicks;

	private HandViewHooks() {
	}

	static void install(HandView handView) {
		view = handView;
	}

	// ---- rendering (FirstPersonHandsAndItemsRenderer) ----

	public static void beginHands(float partial) {
		partialTicks = partial;
	}

	/** The hands' lag behind turns; 0 keeps them fixed to the camera. */
	public static float rotationDelay(float degrees) {
		HandViewConfig config = active();
		return config != null && config.noRotationDelay ? 0 : degrees;
	}

	public static boolean hidesHand(ItemStack stack) {
		HandViewConfig config = active();
		return config != null && config.hideEmptyHand && stack.isEmpty();
	}

	/** The swing progress a hand renders with. */
	public static float attack(InteractionHand hand, float attack) {
		return active() == null ? attack : view.swings.attack(hand, attack, partialTicks);
	}

	/** A plain swing while the stub plays, so held items keep swinging after vanilla's swing ended. */
	public static LivingEntity.@Nullable SwingDescription currentSwing(InteractionHand hand, LivingEntity.@Nullable SwingDescription swing) {
		if (active() == null || !view.swings.forcesSwing(hand)) {
			return swing;
		}
		return new LivingEntity.SwingDescription(hand, SwingAnimation.DEFAULT, SwingAnimation.DEFAULT.duration());
	}

	/** Right before a held item is drawn, in its pose. */
	public static void beforeItem(InteractionHand hand, PoseStack pose) {
		HandViewConfig config = active();
		LocalPlayer player = Minecraft.getInstance().player;
		if (config == null || player == null) {
			return;
		}
		HumanoidArm arm = armOf(player, hand);
		view.swings.applyMotion(pose, hand, arm, partialTicks);
		if (config.serverRotations) {
			view.rotation.apply(pose, player);
		}
		// The eating pose is already turned into the middle, where hand transforms would push the food off centre:
		// they fade out as the eating pose fades in
		float eat = EatPose.progress(config, arm, view.eats, partialTicks);
		float eatScale = 1 + ((float) config.eating.scale - 1) * eat;
		if (eat > 0 && eatScale != 1) {
			view.pose.scaleAroundModel(pose, player, player.getItemInHand(hand), arm, eatScale);
		}
		if (eat < 1) {
			HandPose.apply(pose, hand == InteractionHand.MAIN_HAND ? config.mainHand : config.offHand, 1 - eat);
		}
	}

	/** Before vanilla places a held item by the arm, in camera space: the way back from eating, unless {@code using}. */
	public static void beforeItemArm(PoseStack pose, HumanoidArm arm, boolean using) {
		HandViewConfig config = active();
		if (config == null || using || !EatPose.smooth(config)) {
			return;
		}
		view.eats.noteNotEating(arm, partialTicks);
		float level = EatPose.progress(config, arm, view.eats, partialTicks);
		if (level <= 0) {
			return;
		}
		HandPose.translate(pose, config.eating.offset, level);
		pose.translate(0, view.eats.bob(arm, partialTicks), 0);
		EatPose.apply(pose, arm, level);
	}

	/** Right before a bare arm is drawn, in its pose. */
	public static void beforeArm(PoseStack pose, HumanoidArm arm) {
		HandViewConfig config = active();
		LocalPlayer player = Minecraft.getInstance().player;
		if (config == null || player == null) {
			return;
		}
		view.swings.applyMotion(pose, arm == player.getMainArm() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND, arm, partialTicks);
		HandPose.apply(pose, config.arm, 1);
	}

	/** Whether vanilla draws {@code hand} in a use pose this frame (its own test, on the render state). */
	public static boolean inUse(PlayerRenderState player, FirstPersonHandsAndItemsRenderState hands, InteractionHand hand) {
		AvatarRenderState avatar = player.avatarRenderState;
		return avatar != null && avatar.isUsingItem && hands.useItemRemainingTicks > 0 && avatar.useItemHand == hand;
	}

	// ---- eating (applyEatTransform) ----

	public static boolean eatDisabled() {
		HandViewConfig config = active();
		return config != null && config.eating.disableAnimation;
	}

	/** Before vanilla's eating pose, in camera space. */
	public static void beforeEat(PoseStack pose, HumanoidArm arm, float remainingTicks, int useDuration) {
		HandViewConfig config = active();
		if (config == null || config.eating.disableAnimation) {
			return;
		}
		float level = EatPose.blend(config, arm, view.eats, remainingTicks - partialTicks + 1, useDuration, partialTicks);
		view.eats.noteEating(arm, level, partialTicks);
		HandPose.translate(pose, config.eating.offset, level);
	}

	/** Vanilla's {@code pow(left, 27)}: the eat pose is {@code 1 - pow}, so the smooth curve answers {@code 1 - blend}. */
	public static double eatPow(double left, double exponent, int useDuration, HumanoidArm arm, DoubleBinaryOperator vanilla) {
		HandViewConfig config = active();
		if (config == null || !EatPose.smooth(config)) {
			return vanilla.applyAsDouble(left, exponent);
		}
		return 1 - Math.max(EatCurve.smoothBlend(left, useDuration), view.eats.level(arm, partialTicks));
	}

	/** The eating pose's up-and-down bob; a smooth one fades in, from what is left of the last use's. */
	public static float eatBob(float bob, float remainingTicks, float partial, int useDuration, HumanoidArm arm) {
		HandViewConfig config = active();
		if (config == null) {
			return bob;
		}
		float scaled = bob * (float) config.eating.bob;
		if (EatPose.smooth(config)) {
			scaled *= (float) EatCurve.smoothBobFade((remainingTicks - partial + 1) / useDuration, useDuration);
			scaled = Math.max(scaled, view.eats.bob(arm, partial));
		}
		view.eats.noteBob(arm, scaled);
		return scaled;
	}

	// ---- equipping (FirstPersonHandsAndItems) ----

	public static boolean skipsSwap() {
		HandViewConfig config = active();
		return config != null && config.skipSwapping;
	}

	/**
	 * The main hand's height step this tick: always raised (1.8's look, and the stub's, which must not dip with the
	 * attack cooldown) or instant, else vanilla's.
	 */
	public static float mainHeightStep(float step, LocalPlayer player, ItemStack shownItem, float height) {
		HandViewConfig config = active();
		boolean ignoresCooldown = config != null && (config.swing.oldAnimations || config.swing.leftClick == SwingConfig.LeftClickAnimation.STUB);
		if (config == null || (!config.skipSwapping && !ignoresCooldown)) {
			return step;
		}
		float swap = player.getItemSwapScale(1.0F);
		float raised = ignoresCooldown ? 1 : swap * swap * swap;
		boolean same = shownItem == player.getMainHandItem() || config.skipSwapping;
		return (same ? raised : 0) - height;
	}

	public static float offHeightStep(float step, float height) {
		return skipsSwap() ? 1 - height : step;
	}

	/** Smooth right clicks keep a stack that only changed its count (a bite, a throw) raised instead of re-equipping it. */
	public static boolean keepsShownItem(ItemStack visible, ItemStack next) {
		return keepsHeightOnUse() && ItemStack.isSameItemSameComponents(visible, next);
	}

	/** Smooth right clicks keep the item where it is instead of dropping it after a use. */
	public static boolean keepsHeightOnUse() {
		HandViewConfig config = active();
		return config != null && EatPose.smooth(config);
	}

	// ---- swings (LivingEntity, Minecraft) ----

	public static InteractionHand swingHand(LivingEntity entity, InteractionHand hand) {
		HandViewConfig config = active();
		if (config == null || entity != Minecraft.getInstance().player) {
			return hand;
		}
		return switch (config.swing.swingHand) {
			case VANILLA -> hand;
			case MAIN_HAND -> InteractionHand.MAIN_HAND;
			case OFF_HAND -> InteractionHand.OFF_HAND;
		};
	}

	public static int swingDuration(LivingEntity entity, int duration) {
		HandViewConfig config = active();
		Minecraft minecraft = Minecraft.getInstance();
		if (config == null || entity != minecraft.player || !minecraft.options.getCameraType().isFirstPerson()) {
			return duration;
		}
		return config.swing.swingTicks;
	}

	/** After an attack or mining swing was asked for; {@code started} when it played. */
	public static void onLeftSwing(boolean started) {
		HandViewConfig config = active();
		LocalPlayer player = Minecraft.getInstance().player;
		if (started && config != null && player != null) {
			view.swings.onLeftSwing(config.swing, player);
		}
	}

	public static void beforeUseItemOn(LocalPlayer player, InteractionHand hand, BlockHitResult hit) {
		Minecraft minecraft = Minecraft.getInstance();
		if (active() != null && minecraft.level != null) {
			view.beforeUseItemOn(minecraft.level, player, hand, hit);
		}
	}

	public static void afterUseItemOn() {
		Minecraft minecraft = Minecraft.getInstance();
		if (active() != null && minecraft.level != null) {
			view.afterUseItemOn(minecraft.level);
		}
	}

	/** After a right click swing on an entity. */
	public static void onInteractEntitySwing(boolean started) {
		onRightSwing(started, SwingAnimator.RightClick.INTERACT);
	}

	/** After a right click swing on a block: a placement if a block appeared, else an interaction. */
	public static void onUseBlockSwing(boolean started) {
		onRightSwing(started, SwingAnimator.RightClick.PLACE);
	}

	/** After a right click swing of the item itself (throwing, ...). */
	public static void onUseItemSwing(boolean started) {
		onRightSwing(started, SwingAnimator.RightClick.USE);
	}

	private static void onRightSwing(boolean started, SwingAnimator.RightClick kind) {
		HandViewConfig config = active();
		LocalPlayer player = Minecraft.getInstance().player;
		if (started && config != null && player != null) {
			view.swings.onRightSwing(config.swing, player, kind);
		}
	}

	private static @Nullable HandViewConfig active() {
		return view == null ? null : view.active();
	}

	private static HumanoidArm armOf(LocalPlayer player, InteractionHand hand) {
		return hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
	}
}
