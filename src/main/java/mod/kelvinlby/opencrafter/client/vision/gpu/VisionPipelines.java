package mod.kelvinlby.opencrafter.client.vision.gpu;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import mod.kelvinlby.opencrafter.OpenCrafter;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;

import java.util.Optional;

/**
 * The vision sensor's own render pipelines. They only use renderpearl abstractions, so they
 * run unchanged on OpenGL and Vulkan, and they never touch vanilla pipelines, so shader mods
 * replacing those do not affect them.
 */
public final class VisionPipelines {
	public static final RenderPipeline RGB = RenderPipelines.registerOptional(
		fullscreen("vision_rgb", true)
			.withFragmentShader(OpenCrafter.id("core/vision_rgb"))
			.withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
			.withColorTargetState(target(GpuFormat.RGBA8_UNORM))
			.build()
	);

	private VisionPipelines() {
	}

	/** Forces class initialisation so the pipelines are compiled with the next resource reload. */
	public static void bootstrap() {
	}

	static RenderPipeline.Builder fullscreen(String name, boolean flipY) {
		RenderPipeline.Builder builder = RenderPipeline.builder()
			.withLocation(OpenCrafter.id("pipeline/" + name))
			.withVertexShader(OpenCrafter.id("core/vision_fullscreen"))
			.withPrimitiveTopology(PrimitiveTopology.TRIANGLES);
		return flipY ? builder.withShaderDefine("FLIP_Y") : builder;
	}

	static ColorTargetState target(GpuFormat format) {
		return new ColorTargetState(Optional.empty(), format, ColorTargetState.WRITE_ALL);
	}
}
