package mod.kelvinlby.opencrafter.client.handview;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import mod.kelvinlby.opencrafter.core.config.handview.HandTransformConfig;
import mod.kelvinlby.opencrafter.core.config.handview.Vec3Config;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;

/** Pose edits for the configured hand transforms; a weight of 0 changes nothing, 1 applies fully. */
final class HandPose {
	private final ItemStackRenderState probe = new ItemStackRenderState();

	/** Rotation, then scale, then position, faded by {@code weight}. */
	static void apply(PoseStack pose, HandTransformConfig transform, float weight) {
		rotate(pose, transform.rotation, weight);
		scale(pose, transform.scale, weight);
		translate(pose, transform.position, weight);
	}

	static void translate(PoseStack pose, Vec3Config offset, float weight) {
		pose.translate((float) offset.x * weight, (float) offset.y * weight, (float) offset.z * weight);
	}

	private static void rotate(PoseStack pose, Vec3Config degrees, float weight) {
		pose.rotateDegrees(Axis.XP, (float) degrees.x * weight);
		pose.rotateDegrees(Axis.YP, (float) degrees.y * weight);
		pose.rotateDegrees(Axis.ZP, (float) degrees.z * weight);
	}

	private static void scale(PoseStack pose, Vec3Config scale, float weight) {
		pose.scale(blend(scale.x, weight), blend(scale.y, weight), blend(scale.z, weight));
	}

	private static float blend(double factor, float weight) {
		return 1 + ((float) factor - 1) * weight;
	}

	/** Scales about the held model's visual centre, which its display transform moves off the pose origin. */
	void scaleAroundModel(PoseStack pose, LocalPlayer player, ItemStack stack, HumanoidArm arm, float scale) {
		Vec3 centre = modelCentre(player, stack, arm);
		boolean finite = Double.isFinite(centre.x) && Double.isFinite(centre.y) && Double.isFinite(centre.z);
		if (finite) {
			pose.translate(centre.x, centre.y, centre.z);
		}
		pose.scale(scale, scale, scale);
		if (finite) {
			pose.translate(-centre.x, -centre.y, -centre.z);
		}
	}

	private Vec3 modelCentre(LocalPlayer player, ItemStack stack, HumanoidArm arm) {
		Minecraft minecraft = Minecraft.getInstance();
		ItemDisplayContext context = arm == HumanoidArm.RIGHT ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
		probe.clear();
		minecraft.getItemModelResolver().updateForTopItem(probe, stack, context, minecraft.level, player, player.getId() + context.ordinal());
		return probe.getModelBoundingBox().getCenter();
	}
}
