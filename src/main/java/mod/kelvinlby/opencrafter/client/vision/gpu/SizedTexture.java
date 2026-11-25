package mod.kelvinlby.opencrafter.client.vision.gpu;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.GpuFormat;
import com.mojang.renderpearl.api.device.GpuDevice;
import com.mojang.renderpearl.api.textures.GpuTexture;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import org.jspecify.annotations.Nullable;

/** A render-target texture that follows the framebuffer size; recreated only when the size changes. */
public final class SizedTexture implements AutoCloseable {
	private static final int USAGE = GpuTexture.USAGE_COPY_SRC | GpuTexture.USAGE_COPY_DST
		| GpuTexture.USAGE_TEXTURE_BINDING | GpuTexture.USAGE_RENDER_ATTACHMENT;

	private final String label;
	private final GpuFormat format;
	private @Nullable GpuTexture texture;
	private @Nullable GpuTextureView view;

	public SizedTexture(String label, GpuFormat format) {
		this.label = label;
		this.format = format;
	}

	public void ensureSize(int width, int height) {
		if (texture != null && texture.getWidth(0) == width && texture.getHeight(0) == height) {
			return;
		}
		close();
		GpuDevice device = RenderSystem.getDevice();
		texture = device.createTexture("open-crafter " + label, USAGE, format, width, height, 1, 1);
		view = device.createTextureView(texture);
	}

	public GpuTexture texture() {
		if (texture == null) {
			throw new IllegalStateException(label + " not allocated");
		}
		return texture;
	}

	public GpuTextureView view() {
		if (view == null) {
			throw new IllegalStateException(label + " not allocated");
		}
		return view;
	}

	public boolean isAllocated() {
		return texture != null;
	}

	@Override
	public void close() {
		if (view != null) {
			view.close();
			view = null;
		}
		if (texture != null) {
			texture.close();
			texture = null;
		}
	}
}
