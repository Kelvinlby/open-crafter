package mod.kelvinlby.opencrafter.client.handview;

import mod.kelvinlby.opencrafter.client.handview.anim.Box;
import mod.kelvinlby.opencrafter.client.handview.anim.CubeRotation;
import mod.kelvinlby.opencrafter.mixin.handview.ItemStackRenderStateAccessor;
import mod.kelvinlby.opencrafter.mixin.handview.LayerRenderStateAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.List;

/** The faces of a held item's model and of a placed block's model, in block units. */
final class ModelFaces {
	private static final ItemStackRenderState HELD = new ItemStackRenderState();

	private ModelFaces() {
	}

	/** The model {@code stack} is drawn with in first person by {@code arm}; reused, read it before the next call. */
	static ItemStackRenderState heldModel(LocalPlayer player, ItemStack stack, HumanoidArm arm) {
		Minecraft minecraft = Minecraft.getInstance();
		ItemDisplayContext context = arm == HumanoidArm.RIGHT ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;
		HELD.clear();
		minecraft.getItemModelResolver().updateForTopItem(HELD, stack, context, minecraft.level, player, player.getId() + context.ordinal());
		return HELD;
	}

	/** Every quad of the held item's layers, in its model space. */
	static List<BakedQuad> itemQuads(ItemStackRenderState item) {
		List<BakedQuad> quads = new ArrayList<>();
		for (LayerRenderStateAccessor layer : layers(item)) {
			quads.addAll(layer.openCrafter$quads().all());
		}
		return quads;
	}

	/** True when the item is drawn as block geometry: 3D-lit quads, no special renderer (chests, beds). */
	static boolean isBlockModel(ItemStackRenderState item) {
		List<LayerRenderStateAccessor> layers = layers(item);
		if (layers.isEmpty() || !item.usesBlockLight()) {
			return false;
		}
		for (LayerRenderStateAccessor layer : layers) {
			if (layer.openCrafter$specialRenderer() != null || layer.openCrafter$quads().isEmpty()) {
				return false;
			}
		}
		return true;
	}

	/** The placed block's quads, as the world draws them. */
	static List<BakedQuad> blockQuads(BlockState state, BlockPos pos) {
		BlockStateModel model = Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
		List<BlockStateModelPart> parts = new ArrayList<>();
		model.collectParts(RandomSource.create(state.getSeed(pos)), parts);
		List<BakedQuad> quads = new ArrayList<>();
		for (BlockStateModelPart part : parts) {
			for (Direction direction : Direction.values()) {
				quads.addAll(part.getQuads(direction));
			}
			quads.addAll(part.getQuads(null));
		}
		return quads;
	}

	static List<CubeRotation.Face> faces(List<BakedQuad> quads) {
		List<CubeRotation.Face> faces = new ArrayList<>(quads.size());
		for (BakedQuad quad : quads) {
			faces.add(new CubeRotation.Face(centre(quad), quad.direction().step(), quad.materialInfo().sprite()));
		}
		return faces;
	}

	static Box bounds(List<BakedQuad> quads) {
		Vector3f min = new Vector3f(Float.POSITIVE_INFINITY);
		Vector3f max = new Vector3f(Float.NEGATIVE_INFINITY);
		for (BakedQuad quad : quads) {
			for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
				min.min(quad.position(vertex));
				max.max(quad.position(vertex));
			}
		}
		return new Box(min, max);
	}

	static List<LayerRenderStateAccessor> layers(ItemStackRenderState item) {
		ItemStackRenderStateAccessor accessor = (ItemStackRenderStateAccessor) item;
		ItemStackRenderState.LayerRenderState[] all = accessor.openCrafter$layers();
		List<LayerRenderStateAccessor> layers = new ArrayList<>();
		for (int i = 0; i < accessor.openCrafter$activeLayerCount(); i++) {
			layers.add((LayerRenderStateAccessor) (Object) all[i]);
		}
		return layers;
	}

	private static Vector3f centre(BakedQuad quad) {
		Vector3f sum = new Vector3f();
		for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
			sum.add(quad.position(vertex));
		}
		return sum.div(BakedQuad.VERTEX_COUNT);
	}
}
