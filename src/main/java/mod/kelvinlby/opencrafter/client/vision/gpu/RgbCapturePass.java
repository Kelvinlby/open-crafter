package mod.kelvinlby.opencrafter.client.vision.gpu;

import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;

/** Copies the final presented image into an RGBA8 texture laid out top row first, alpha forced to 1. */
public final class RgbCapturePass implements AutoCloseable {
	private final SizedTexture output = new SizedTexture("rgb capture", GpuFormat.RGBA8_UNORM);

	public GpuTexture run(GpuTextureView finalImage, int width, int height) {
		output.ensureSize(width, height);
		FullscreenPass.draw("rgb capture", VisionPipelines.RGB,
			pass -> pass.setUniform("InSampler", finalImage, FullscreenPass.nearest()),
			output.view());
		return output.texture();
	}

	@Override
	public void close() {
		output.close();
	}
}
