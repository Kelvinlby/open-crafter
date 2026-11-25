package mod.kelvinlby.opencrafter.client.vision.gui;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import mod.kelvinlby.opencrafter.client.vision.gpu.FullscreenPass;
import mod.kelvinlby.opencrafter.client.vision.gpu.SizedTexture;
import mod.kelvinlby.opencrafter.client.vision.gpu.VisionPipelines;
import net.minecraft.client.renderer.Projection;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * Flow of GUI elements between two captured frames, in two textures on the render-target grid:
 * <ul>
 *   <li>{@link #flowView()}: for pixels of the reference frame covered by a GUI element that moved
 *       or vanished: (dx, dy) in pixels with y down, occluded flag, 1;</li>
 *   <li>{@link #coverView()}: 1 where the current frame shows a GUI element that moved there or
 *       newly appeared (whatever was under it in the reference frame is now hidden).</li>
 * </ul>
 * Elements that stayed put are not drawn: their pixels keep the HUD rule (flow 0).
 */
public final class GuiFlowRenderer implements AutoCloseable {
	private static final String UNIFORM_BLOCK = VisionPipelines.GUI_UNIFORM_BLOCK;
	private static final int UNIFORM_SIZE = 64 + 16;

	private final SizedTexture flow = new SizedTexture("gui flow", VisionPipelines.FLOW_WORK_FORMAT);
	private final SizedTexture cover = new SizedTexture("gui cover", VisionPipelines.MASK_FORMAT);
	private final GuiMeshBuffer motionMesh = new GuiMeshBuffer("gui motion mesh");
	private final GuiMeshBuffer coverMesh = new GuiMeshBuffer("gui cover mesh");
	private GpuBuffer uniforms;

	private record Batch(GuiElement element, int firstVertex, int vertexCount, float[] scissor) {
	}

	public GpuTextureView flowView() {
		return flow.view();
	}

	public GpuTextureView coverView() {
		return cover.view();
	}

	public void render(GuiFrame prev, GuiFrame cur, int width, int height) {
		flow.ensureSize(width, height);
		cover.ensureSize(width, height);
		zeroFill();
		GuiMotion motion = GuiMotion.between(prev, cur);
		uploadUniforms(width, height);
		drawMotion(motion, height);
		drawCover(motion, height);
	}

	private void drawMotion(GuiMotion motion, int height) {
		FloatArrayList floats = new FloatArrayList();
		List<Batch> batches = new ArrayList<>();
		List<GuiMotion.Pair> moved = new ArrayList<>(motion.moved());
		motion.vanished().forEach(element -> moved.add(new GuiMotion.Pair(element, null)));
		moved.sort(Comparator.comparingInt(pair -> pair.prev().order));
		for (GuiMotion.Pair pair : moved) {
			if (!GuiMotionMesh.supported(pair.prev())) {
				continue;
			}
			int first = floats.size() / GuiMotionMesh.FLOATS_PER_VERTEX;
			if (pair.cur() == null) {
				GuiMotionMesh.addVanished(floats, pair.prev());
			} else {
				GuiMotionMesh.addMoved(floats, pair.prev(), pair.cur());
			}
			batches.add(new Batch(pair.prev(), first, floats.size() / GuiMotionMesh.FLOATS_PER_VERTEX - first, pair.prev().clip));
		}
		draw("gui motion", flow.view(), motionMesh, floats, batches, false, height);
	}

	private void drawCover(GuiMotion motion, int height) {
		List<GuiElement> covering = new ArrayList<>(motion.appeared());
		motion.moved().forEach(pair -> covering.add(pair.cur()));
		covering.sort(Comparator.comparingInt(element -> element.order));
		FloatArrayList floats = new FloatArrayList();
		List<Batch> batches = new ArrayList<>();
		for (GuiElement element : covering) {
			if (!GuiMotionMesh.supported(element)) {
				continue;
			}
			int first = floats.size() / GuiMotionMesh.FLOATS_PER_VERTEX;
			GuiMotionMesh.addCovering(floats, element);
			batches.add(new Batch(element, first, floats.size() / GuiMotionMesh.FLOATS_PER_VERTEX - first, element.clip));
		}
		draw("gui cover", cover.view(), coverMesh, floats, batches, true, height);
	}

	private void draw(String label, GpuTextureView target, GuiMeshBuffer mesh, FloatArrayList floats, List<Batch> batches, boolean cover, int height) {
		if (batches.isEmpty()) {
			return;
		}
		mesh.upload(floats);
		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder()
				.createRenderPass(() -> "open-crafter " + label, target, Optional.empty())) {
			pass.setUniform(UNIFORM_BLOCK, uniforms);
			for (Batch batch : batches) {
				drawBatch(pass, mesh, batch, cover, height);
			}
		}
	}

	private static void drawBatch(RenderPass pass, GuiMeshBuffer mesh, Batch batch, boolean cover, int height) {
		GuiElement element = batch.element();
		RenderPipeline pipeline = VisionPipelines.guiPass(element.textured(), cover);
		pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
		if (element.textured()) {
			pass.setUniform("Sampler0", element.texture, element.sampler);
		}
		float[] clip = batch.scissor();
		int left = (int) clip[0];
		int bottom = (int) clip[3];
		pass.enableScissor(left, height - bottom, Math.max(0, (int) clip[2] - left), Math.max(0, bottom - (int) clip[1]));
		pass.setVertexBuffer(0, mesh.slice(batch.firstVertex(), batch.vertexCount()));
		pass.draw(batch.vertexCount(), 1, 0, 0);
	}

	/**
	 * Both targets start each frame as zero. Done with a full-screen draw: on OpenGL a target that
	 * only received {@code clearColorTexture} (or a clearing pass without draws) was read back as
	 * (0, 0, 0, 1) by the resolve pass, which made every pixel look like a moving GUI element.
	 */
	private void zeroFill() {
		FullscreenPass.draw("gui flow zero", VisionPipelines.ZERO_FLOAT, pass -> {
		}, flow.view());
		FullscreenPass.draw("gui cover zero", VisionPipelines.ZERO_MASK, pass -> {
		}, cover.view());
	}

	private void uploadUniforms(int width, int height) {
		if (uniforms == null) {
			uniforms = RenderSystem.getDevice().createBuffer(() -> "open-crafter " + UNIFORM_BLOCK,
				GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, UNIFORM_SIZE);
		}
		Projection projection = new Projection();
		projection.setupOrtho(1000.0F, 11000.0F, width, height, true);
		try (MemoryStack stack = MemoryStack.stackPush()) {
			Std140Builder builder = Std140Builder.onStack(stack, UNIFORM_SIZE);
			builder.putMat4f(projection.getMatrix(new Matrix4f()));
			builder.putVec4(width, height, 0.0F, 0.0F);
			RenderSystem.getDevice().createCommandEncoder().writeToBuffer(uniforms.slice(), builder.get());
		}
	}

	@Override
	public void close() {
		flow.close();
		cover.close();
		motionMesh.close();
		coverMesh.close();
		if (uniforms != null) {
			uniforms.close();
		}
	}
}
