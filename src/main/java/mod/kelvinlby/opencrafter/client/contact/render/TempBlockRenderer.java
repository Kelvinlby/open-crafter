package mod.kelvinlby.opencrafter.client.contact.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.block.MovingBlockRenderState;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

/**
 * Submits a {@link TempBlock} into the level's feature submission, lit and tinted the way moving
 * blocks (pistons, falling blocks) are, every quad with its own transform. Goes through the
 * vanilla submit pipeline only, so it renders on every backend and with shader packs.
 */
final class TempBlockRenderer {
	private TempBlockRenderer() {
	}

	static void submit(TempBlock block, Function<BakedQuad, Matrix4fc> pose, SubmitNodeCollector collector, Vec3 camera, ClientLevel level) {
		BlockPos pos = block.pos();
		BlockState state = block.state();
		PoseStack poseStack = new PoseStack();
		poseStack.translate(pos.getX() - camera.x, pos.getY() - camera.y, pos.getZ() - camera.z);
		MovingBlockRenderState lighting = lighting(level, pos, state);
		for (ChunkSectionLayer layer : layers(state, ModelQuads.of(state, pos))) {
			collector.submitCustomGeometry(poseStack, renderType(layer), (at, buffer) -> tesselate(at, buffer, lighting, layer, pose));
		}
	}

	/** Runs the block renderer on the model, keeping one layer's quads and moving each by its transform. */
	private static void tesselate(PoseStack.Pose at, VertexConsumer buffer, MovingBlockRenderState lighting, ChunkSectionLayer layer,
		Function<BakedQuad, Matrix4fc> pose) {
		Minecraft minecraft = Minecraft.getInstance();
		ModelBlockRenderer renderer = new ModelBlockRenderer(minecraft.options.ambientOcclusion().get(), false, minecraft.getBlockColors());
		BlockState state = lighting.blockState;
		boolean opaque = ModelBlockRenderer.forceOpaque(minecraft.options.cutoutLeaves().get(), state);
		PoseStack quadPose = new PoseStack();
		renderer.tesselateBlock((x, y, z, quad, instance) -> {
			if (layerOf(quad, opaque) == layer) {
				Matrix4fc transform = pose.apply(quad);
				instance.scaleColor(FaceShade.correction(lighting.cardinalLighting, quad, transform));
				putQuad(quadPose, at, transform, x, y, z, quad, instance, buffer);
			}
		}, 0.0F, 0.0F, 0.0F, lighting, lighting.blockPos, state, ModelQuads.model(state), state.getSeed(lighting.randomSeedPos));
	}

	private static void putQuad(PoseStack quadPose, PoseStack.Pose at, Matrix4fc transform, float x, float y, float z, BakedQuad quad,
		QuadInstance instance, VertexConsumer buffer) {
		quadPose.setIdentity();
		quadPose.last().set(at);
		quadPose.mulPose(transform);
		quadPose.translate(x, y, z);
		buffer.putBakedQuad(quadPose.last(), quad, instance);
	}

	/** Light, tint and shading as the world has them at the block, with nothing around it to cull against. */
	private static MovingBlockRenderState lighting(ClientLevel level, BlockPos pos, BlockState state) {
		MovingBlockRenderState lighting = new MovingBlockRenderState();
		lighting.randomSeedPos = pos;
		lighting.blockPos = pos;
		lighting.blockState = state;
		lighting.biome = level.getBiome(pos);
		lighting.cardinalLighting = level.cardinalLighting();
		lighting.lightEngine = level.getLightEngine();
		return lighting;
	}

	private static Set<ChunkSectionLayer> layers(BlockState state, List<BakedQuad> quads) {
		boolean opaque = ModelBlockRenderer.forceOpaque(Minecraft.getInstance().options.cutoutLeaves().get(), state);
		Set<ChunkSectionLayer> layers = EnumSet.noneOf(ChunkSectionLayer.class);
		quads.forEach(quad -> layers.add(layerOf(quad, opaque)));
		return layers;
	}

	private static ChunkSectionLayer layerOf(BakedQuad quad, boolean opaque) {
		return opaque ? ChunkSectionLayer.SOLID : quad.materialInfo().layer();
	}

	private static RenderType renderType(ChunkSectionLayer layer) {
		return switch (layer) {
			case SOLID -> RenderTypes.solidMovingBlock();
			case CUTOUT -> RenderTypes.cutoutMovingBlock();
			case TRANSLUCENT -> RenderTypes.translucentMovingBlock();
		};
	}
}
