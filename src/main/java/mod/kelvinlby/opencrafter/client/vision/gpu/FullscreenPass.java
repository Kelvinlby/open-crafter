package mod.kelvinlby.opencrafter.client.vision.gpu;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.commands.RenderPassDescriptor;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.FilterMode;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;

import java.util.function.Consumer;

/** Runs one full-screen triangle with a vision pipeline into one or more color targets. */
public final class FullscreenPass {
	private FullscreenPass() {
	}

	public static void draw(String label, RenderPipeline pipeline, Consumer<RenderPass> bindings, GpuTextureView... targets) {
		RenderPassDescriptor.Builder descriptor = RenderPassDescriptor.builder(() -> "open-crafter " + label);
		for (GpuTextureView target : targets) {
			descriptor.withColorAttachment(target);
		}
		try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(descriptor.build())) {
			pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
			bindings.accept(pass);
			pass.draw(3, 1, 0, 0);
		}
	}

	public static GpuSampler nearest() {
		return RenderSystem.getSamplerCache().getClampToEdge(FilterMode.NEAREST);
	}
}
