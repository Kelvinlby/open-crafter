package mod.kelvinlby.opencrafter.client.vision.hud;

import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import mod.kelvinlby.opencrafter.OpenCrafter;
import mod.kelvinlby.opencrafter.client.vision.gpu.VisionPipelines;
import net.minecraft.client.renderer.BindGroupLayouts;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Mask pipelines matching the vertex layout of each GUI pipeline they replay. Built on first
 * use and cached; the shader binds attributes by name, so any GUI vertex format works.
 */
final class GuiMaskPipelines {
	private record Variant(VertexFormat format, PrimitiveTopology topology, boolean textured, boolean colored) {
	}

	private final Map<Variant, RenderPipeline> pipelines = new HashMap<>();

	RenderPipeline forDraw(RenderPipeline guiPipeline, boolean hasTexture) {
		VertexFormat format = guiPipeline.getVertexFormatBinding(0);
		Variant variant = new Variant(format, guiPipeline.getPrimitiveTopology(),
			hasTexture && format.contains("UV0"), format.contains("Color"));
		return pipelines.computeIfAbsent(variant, GuiMaskPipelines::build);
	}

	private static RenderPipeline build(Variant variant) {
		String name = String.format(Locale.ROOT, "pipeline/vision_gui_mask_%08x", variant.hashCode());
		RenderPipeline.Builder builder = RenderPipeline.builder()
			.withLocation(OpenCrafter.id(name))
			.withVertexShader(OpenCrafter.id("core/vision_gui_mask"))
			.withFragmentShader(OpenCrafter.id("core/vision_gui_mask"))
			.withBindGroupLayout(BindGroupLayouts.PROJECTION)
			.withBindGroupLayout(BindGroupLayouts.DYNAMIC_TRANSFORMS)
			.withVertexBinding(0, variant.format())
			.withPrimitiveTopology(variant.topology())
			.withCull(false)
			.withColorTargetState(new ColorTargetState(Optional.empty(), VisionPipelines.MASK_FORMAT, ColorTargetState.WRITE_ALL));
		if (variant.textured()) {
			builder.withShaderDefine("HAS_UV0").withBindGroupLayout(BindGroupLayouts.SAMPLER0);
		}
		if (variant.colored()) {
			builder.withShaderDefine("HAS_COLOR");
		}
		return builder.build();
	}
}
