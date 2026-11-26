package mod.kelvinlby.opencrafter.client.handview;

import com.mojang.blaze3d.vertex.PoseStack;
import mod.kelvinlby.opencrafter.client.handview.anim.Box;
import mod.kelvinlby.opencrafter.client.handview.anim.CubeRotation;
import mod.kelvinlby.opencrafter.client.handview.anim.PoseBlend;
import mod.kelvinlby.opencrafter.mixin.handview.LayerRenderStateAccessor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Block placement: the held block item turns, moves and resizes until it is the placed block,
 * in place and in shape. Works for items drawn as block geometry; flat icons jab instead.
 */
final class BlockMorph {
	/** Where the held model ends up (its model space mapped into the placed block's cell), and its centre. */
	record Target(BlockPos pos, Matrix4f modelToBlock, Vector3f centre) {
	}

	/** Where vanilla holds an item ({@code applyItemArmTransform}, item fully raised). */
	private static final float REST_X = 0.56F;
	private static final float REST_Y = -0.52F;
	private static final float REST_Z = -0.72F;

	private BlockMorph() {
	}

	/** The morph's end for this item and block, or null when the item cannot become the block. */
	static @Nullable Target resolve(ItemStackRenderState item, PlacedBlock placed, ClientLevel level) {
		if (!ModelFaces.isBlockModel(item)) {
			return null;
		}
		List<BakedQuad> itemQuads = ModelFaces.itemQuads(item);
		List<BakedQuad> blockQuads = ModelFaces.blockQuads(placed.state(), placed.pos());
		Box model = ModelFaces.bounds(itemQuads);
		Vector3f centre = model.centre();
		Matrix4f turn = CubeRotation.find(ModelFaces.faces(itemQuads), ModelFaces.faces(blockQuads));
		if (turn != null) {
			return new Target(placed.pos(), turn, centre);
		}
		Box added = Box.added(outline(placed.state(), level, placed.pos()), outline(placed.before(), level, placed.pos()));
		return added.isEmpty() ? null : new Target(placed.pos(), model.fitInto(added), centre);
	}

	/**
	 * Draws a flight {@code weight} of the way from the hand to its block. Call at the end of the hand
	 * pass, with {@code pose} back at the pass's starting pose.
	 */
	static void draw(PoseStack pose, SubmitNodeCollector collector, int light, MorphFlight flight, HandFrame frame, float weight) {
		Matrix4f display = displayTransform(flight.model);
		Matrix4f fov = HandSpace.fovCorrection();
		Target target = flight.target;
		Matrix4f fromCamera = new Matrix4f(flight.from).mul(display);
		Matrix4f toCamera = frame.bob().mul(HandSpace.viewRotation()).translate(HandSpace.offsetFromEye(target.pos())).mul(target.modelToBlock());
		Matrix4f placed = new Matrix4f(frame.modelView()).invert().mul(fov).mul(PoseBlend.blend(fromCamera, toCamera, weight, target.centre()));
		pose.pushPose();
		pose.mulPose(new Matrix4f(pose.last().pose()).invert().mul(placed).mul(display.invert()));
		flight.model.submit(pose, collector, light, OverlayTexture.NO_OVERLAY, 0);
		pose.popPose();
	}

	/** A hand pose (before the item's display transform) in camera space as the world draws it. */
	static Matrix4f cameraPose(HandFrame frame, Matrix4fc handPose) {
		return HandSpace.fovCorrection().invert().mul(frame.modelView()).mul(handPose);
	}

	/** Where vanilla holds an item by {@code arm}, in camera space; for a flight from an empty hand. */
	static Matrix4f restCameraPose(HandFrame frame, HumanoidArm arm) {
		float side = arm == HumanoidArm.RIGHT ? 1 : -1;
		return cameraPose(frame, frame.pose().translate(side * REST_X, REST_Y, REST_Z));
	}

	private static Matrix4f displayTransform(ItemStackRenderState item) {
		List<LayerRenderStateAccessor> layers = ModelFaces.layers(item);
		PoseStack.Pose transform = new PoseStack.Pose();
		layers.getFirst().openCrafter$applyTransform(transform);
		return new Matrix4f(transform.pose());
	}

	private static Box outline(BlockState state, ClientLevel level, BlockPos pos) {
		VoxelShape shape = state.getShape(level, pos);
		if (shape.isEmpty()) {
			return new Box(new Vector3f(), new Vector3f());
		}
		AABB bounds = shape.bounds();
		return new Box(new Vector3f((float) bounds.minX, (float) bounds.minY, (float) bounds.minZ),
			new Vector3f((float) bounds.maxX, (float) bounds.maxY, (float) bounds.maxZ));
	}
}
