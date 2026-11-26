package mod.kelvinlby.opencrafter.client.handview;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import mod.kelvinlby.opencrafter.client.handview.anim.EatCurve;
import mod.kelvinlby.opencrafter.core.config.handview.HandViewConfig;
import mod.kelvinlby.opencrafter.core.config.handview.SwingConfig;
import net.minecraft.world.entity.HumanoidArm;

/** Where the eating/drinking pose stands, following vanilla's or the smooth curve, and its way back. */
final class EatPose {
	private EatPose() {
	}

	static boolean smooth(HandViewConfig config) {
		return config.swing.rightClick == SwingConfig.RightClickAnimation.SMOOTH;
	}

	/** How far {@code arm} is in the eating pose this frame: 0 the normal pose, 1 fully in the middle. */
	static float progress(HandViewConfig config, HumanoidArm arm, EatRelease release, float partialTicks) {
		return config.eating.disableAnimation ? 0 : release.current(arm, partialTicks);
	}

	/** The eat curve at {@code remaining} use ticks; a smooth one never drops below the way back of the last use. */
	static float blend(HandViewConfig config, HumanoidArm arm, EatRelease release, double remaining, int useDuration, float partialTicks) {
		float curve = EatCurve.blend(remaining, useDuration, smooth(config));
		return smooth(config) ? Math.max(curve, release.level(arm, partialTicks)) : curve;
	}

	/** Vanilla's eating pose without its bob, {@code level} of the way into the middle. */
	static void apply(PoseStack pose, HumanoidArm arm, float level) {
		int invert = arm == HumanoidArm.RIGHT ? 1 : -1;
		pose.translate(level * 0.6F * invert, level * -0.5F, 0);
		pose.rotateDegrees(Axis.YP, invert * level * 90.0F);
		pose.rotateDegrees(Axis.XP, level * 10.0F);
		pose.rotateDegrees(Axis.ZP, invert * level * 30.0F);
	}
}
