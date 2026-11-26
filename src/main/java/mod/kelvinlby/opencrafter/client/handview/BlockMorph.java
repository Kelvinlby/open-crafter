package mod.kelvinlby.opencrafter.client.handview;

import com.mojang.blaze3d.vertex.PoseStack;
import mod.kelvinlby.opencrafter.client.handview.anim.Box;
import mod.kelvinlby.opencrafter.client.handview.anim.CubeRotation;
import mod.kelvinlby.opencrafter.client.handview.anim.PoseBlend;
import mod.kelvinlby.opencrafter.mixin.handview.LayerRenderStateAccessor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Block placement: the held block item turns, moves and resizes until it is the placed block,
 * in place and in shape. Works for items drawn as block geometry; flat icons keep the reach.
 */
final class BlockMorph {
	/** Where the held model ends up (its model space mapped into the placed block's cell), and its centre. */
	record Target(BlockPos pos, Matrix4f modelToBlock, Vector3f centre) {
	}

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
	 * Moves the item's pose {@code weight} of the way to the target; call right before the item is drawn.
	 * {@code frame}: the hand pass's frame when it began (its starting pose and model-view matrix), which
	 * differs between vanilla and shader mods such as Iris.
	 */
	static void apply(PoseStack pose, ItemStackRenderState item, Target target, HandFrame frame, float weight) {
		Matrix4f display = displayTransform(item);
		Matrix4f current = new Matrix4f(pose.last().pose());
		Matrix4f fov = HandSpace.fovCorrection();
		Matrix4f fromCamera = new Matrix4f(fov).invert().mul(frame.modelView()).mul(current).mul(display);
		Matrix4f toCamera = frame.bob().mul(HandSpace.viewRotation()).translate(HandSpace.offsetFromEye(target.pos())).mul(target.modelToBlock());
		Matrix4f blended = new Matrix4f(frame.modelView()).invert().mul(fov).mul(PoseBlend.blend(fromCamera, toCamera, weight, target.centre()));
		pose.mulPose(current.invert().mul(blended).mul(display.invert()));
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
