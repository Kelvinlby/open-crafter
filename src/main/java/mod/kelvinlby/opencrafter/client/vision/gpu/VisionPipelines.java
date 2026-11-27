package mod.kelvinlby.opencrafter.client.vision.gpu;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.pipeline.BindGroupLayout;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.UniformType;
import com.mojang.renderpearl.api.vertex.VertexFormat;
import mod.kelvinlby.opencrafter.OpenCrafter;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;

import java.util.Optional;

/**
 * The vision sensor's own render pipelines. They only use renderpearl abstractions, so they
 * run unchanged on OpenGL and Vulkan, and they never touch vanilla pipelines, so shader mods
 * replacing those do not affect them. Registered as optional: a failure never blocks the game.
 */
public final class VisionPipelines {
	/** Name of the uniform block in shaders/include/vision_flow.glsl. */
	public static final String FLOW_UNIFORM_BLOCK = "VisionFlow";
	public static final GpuFormat DEPTH_COPY_FORMAT = GpuFormat.R32_FLOAT;
	public static final GpuFormat MASK_FORMAT = GpuFormat.R8_UNORM;
	public static final GpuFormat FLOW_WORK_FORMAT = GpuFormat.RGBA32_FLOAT;
	public static final GpuFormat FLOW_FORMAT = GpuFormat.RG32_FLOAT;
	public static final GpuFormat OCCLUDED_FORMAT = GpuFormat.R8_UNORM;

	/** Vertex layout of recorded moving geometry: view-space position in the reference and current frame. */
	public static final VertexFormat MOTION_FORMAT = VertexFormat.builder(0)
		.addAttribute("PrevPosition", GpuFormat.RGB32_FLOAT)
		.addAttribute("CurPosition", GpuFormat.RGBA32_FLOAT)
		.build();

	/** Name of the uniform block in shaders/core/vision_gui_motion.*. */
	public static final String GUI_UNIFORM_BLOCK = "VisionGui";

	/** Vertex layout of GUI elements for the GUI flow passes (see GuiMotionMesh). */
	public static final VertexFormat GUI_MOTION_FORMAT = VertexFormat.builder(0)
		.addAttribute("PrevPos", GpuFormat.RG32_FLOAT)
		.addAttribute("CurPos", GpuFormat.RG32_FLOAT)
		.addAttribute("UV0", GpuFormat.RG32_FLOAT)
		.addAttribute("Alpha", GpuFormat.R32_FLOAT)
		.addAttribute("Clip", GpuFormat.RGBA32_FLOAT)
		.addAttribute("Vanished", GpuFormat.R32_FLOAT)
		.build();

	private static final BindGroupLayout GUI_UNIFORMS = BindGroupLayout.builder()
		.withUniform(GUI_UNIFORM_BLOCK, UniformType.UNIFORM_BUFFER)
		.build();

	private static final BindGroupLayout FLOW_UNIFORMS = BindGroupLayout.builder()
		.withUniform(FLOW_UNIFORM_BLOCK, UniformType.UNIFORM_BUFFER)
		.build();

	public static final RenderPipeline RGB = RenderPipelines.registerOptional(
		fullscreen("vision_rgb", true)
			.withFragmentShader(OpenCrafter.id("core/vision_rgb"))
			.withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
			.withColorTargetState(target(GpuFormat.RGBA8_UNORM))
			.build()
	);

	public static final RenderPipeline COPY_DEPTH = RenderPipelines.registerOptional(
		fullscreen("vision_copy_depth", false)
			.withFragmentShader(OpenCrafter.id("core/vision_copy_depth"))
			.withBindGroupLayout(BindGroupLayouts.IN_SAMPLER)
			.withColorTargetState(target(DEPTH_COPY_FORMAT))
			.build()
	);

	/** Zero-fills a float render target with a full-screen draw (see GuiFlowRenderer). */
	public static final RenderPipeline ZERO_FLOAT = RenderPipelines.registerOptional(zero("vision_zero_float", FLOW_WORK_FORMAT));
	public static final RenderPipeline ZERO_MASK = RenderPipelines.registerOptional(zero("vision_zero_mask", MASK_FORMAT));

	/** Keeps the depth a shader mod's own hand pass wrote; everything else stays "no hand". */
	public static final RenderPipeline HAND_DEPTH_DIFF = RenderPipelines.registerOptional(
		fullscreen("vision_hand_depth_diff", false)
			.withFragmentShader(OpenCrafter.id("core/vision_hand_depth_diff"))
			.withBindGroupLayout(samplers("InSampler", "PreHandDepth"))
			.withColorTargetState(target(DEPTH_COPY_FORMAT))
			.build()
	);

	public static final RenderPipeline FLOW_STATIC = RenderPipelines.registerOptional(
		fullscreen("vision_flow_static", false)
			.withFragmentShader(OpenCrafter.id("core/vision_flow_static"))
			.withBindGroupLayout(FLOW_UNIFORMS)
			.withBindGroupLayout(samplers("PrevWorldDepth", "PrevHandDepth", "CurWorldDepth", "CurHandDepth"))
			.withColorTargetState(target(FLOW_WORK_FORMAT))
			.build()
	);

	public static final RenderPipeline FLOW_MOTION_WORLD = RenderPipelines.registerOptional(
		motion("vision_flow_motion_world")
			.withBindGroupLayout(samplers("PrevDepth", "CurWorldDepth", "CurHandDepth"))
			.build()
	);

	public static final RenderPipeline FLOW_MOTION_HAND = RenderPipelines.registerOptional(
		motion("vision_flow_motion_hand")
			.withShaderDefine("HAND")
			.withBindGroupLayout(samplers("PrevDepth", "CurHandDepth", "CurWorldDepth", "PrevWorldDepth"))
			.build()
	);

	private static final RenderPipeline GUI_MOTION = RenderPipelines.registerOptional(guiPass("vision_gui_motion", false, false));
	private static final RenderPipeline GUI_MOTION_TEXTURED = RenderPipelines.registerOptional(guiPass("vision_gui_motion_textured", true, false));
	private static final RenderPipeline GUI_COVER = RenderPipelines.registerOptional(guiPass("vision_gui_cover", false, true));
	private static final RenderPipeline GUI_COVER_TEXTURED = RenderPipelines.registerOptional(guiPass("vision_gui_cover_textured", true, true));

	public static final RenderPipeline FLOW_RESOLVE = RenderPipelines.registerOptional(
		fullscreen("vision_flow_resolve", true)
			.withFragmentShader(OpenCrafter.id("core/vision_flow_resolve"))
			.withBindGroupLayout(samplers("FlowWork", "PrevHudMask", "GuiFlow", "CurGuiCover"))
			.withColorTargetState(0, target(FLOW_FORMAT))
			.withColorTargetState(1, target(OCCLUDED_FORMAT))
			.build()
	);

	private VisionPipelines() {
	}

	/** Forces class initialisation so the pipelines are compiled with the next resource reload. */
	public static void bootstrap() {
	}

	private static RenderPipeline zero(String name, GpuFormat format) {
		return fullscreen(name, false)
			.withFragmentShader(OpenCrafter.id("core/vision_zero"))
			.withColorTargetState(target(format))
			.build();
	}

	/** GUI flow pass: motion (where elements were) or cover (where they are now). */
	public static RenderPipeline guiPass(boolean textured, boolean cover) {
		if (cover) {
			return textured ? GUI_COVER_TEXTURED : GUI_COVER;
		}
		return textured ? GUI_MOTION_TEXTURED : GUI_MOTION;
	}

	private static RenderPipeline guiPass(String name, boolean textured, boolean cover) {
		RenderPipeline.Builder builder = RenderPipeline.builder()
			.withLocation(OpenCrafter.id("pipeline/" + name))
			.withVertexShader(OpenCrafter.id("core/vision_gui_motion"))
			.withFragmentShader(OpenCrafter.id("core/vision_gui_motion"))
			.withBindGroupLayout(GUI_UNIFORMS)
			.withVertexBinding(0, GUI_MOTION_FORMAT)
			.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
			.withCull(false)
			.withColorTargetState(target(cover ? MASK_FORMAT : FLOW_WORK_FORMAT));
		if (textured) {
			builder.withShaderDefine("HAS_UV0").withBindGroupLayout(BindGroupLayouts.SAMPLER0);
		}
		if (cover) {
			builder.withShaderDefine("COVER");
		}
		return builder.build();
	}

	static RenderPipeline.Builder fullscreen(String name, boolean flipY) {
		RenderPipeline.Builder builder = RenderPipeline.builder()
			.withLocation(OpenCrafter.id("pipeline/" + name))
			.withVertexShader(OpenCrafter.id("core/vision_fullscreen"))
			.withPrimitiveTopology(PrimitiveTopology.TRIANGLES);
		return flipY ? builder.withShaderDefine("FLIP_Y") : builder;
	}

	private static RenderPipeline.Builder motion(String name) {
		return RenderPipeline.builder()
			.withLocation(OpenCrafter.id("pipeline/" + name))
			.withVertexShader(OpenCrafter.id("core/vision_flow_motion"))
			.withFragmentShader(OpenCrafter.id("core/vision_flow_motion"))
			.withBindGroupLayout(FLOW_UNIFORMS)
			.withVertexBinding(0, MOTION_FORMAT)
			.withPrimitiveTopology(PrimitiveTopology.TRIANGLES)
			.withCull(false)
			.withColorTargetState(target(FLOW_WORK_FORMAT));
	}

	static BindGroupLayout samplers(String... names) {
		BindGroupLayout.Builder builder = BindGroupLayout.builder();
		for (String name : names) {
			builder.withUniform(name, UniformType.COMBINED_IMAGE_SAMPLER);
		}
		return builder.build();
	}

	static ColorTargetState target(GpuFormat format) {
		return new ColorTargetState(Optional.empty(), format, ColorTargetState.WRITE_ALL);
	}
}
