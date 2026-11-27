package mod.kelvinlby.opencrafter.client.contact.render;

import mod.kelvinlby.opencrafter.client.contact.anim.QuadPoints;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.block.dispatch.BlockStateModel;
import net.minecraft.client.renderer.block.dispatch.BlockStateModelPart;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;

/** The baked quads a block state is drawn with at a position (the same instances the block renderer emits). */
public final class ModelQuads {
	private ModelQuads() {
	}

	public static BlockStateModel model(BlockState state) {
		return Minecraft.getInstance().getModelManager().getBlockStateModelSet().get(state);
	}

	public static List<BakedQuad> of(BlockState state, BlockPos pos) {
		List<BlockStateModelPart> parts = new ArrayList<>();
		model(state).collectParts(RandomSource.create(state.getSeed(pos)), parts);
		List<BakedQuad> quads = new ArrayList<>();
		for (BlockStateModelPart part : parts) {
			for (Direction direction : Direction.values()) {
				quads.addAll(part.getQuads(direction));
			}
			quads.addAll(part.getQuads(null));
		}
		return quads;
	}

	public static List<QuadPoints> points(List<BakedQuad> quads) {
		return quads.stream().map(ModelQuads::points).toList();
	}

	private static QuadPoints points(BakedQuad quad) {
		List<Vector3fc> corners = new ArrayList<>(BakedQuad.VERTEX_COUNT);
		for (int vertex = 0; vertex < BakedQuad.VERTEX_COUNT; vertex++) {
			corners.add(quad.position(vertex));
		}
		return new QuadPoints(corners);
	}
}
