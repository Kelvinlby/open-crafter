package mod.kelvinlby.opencrafter.client.vision.gui;

import com.mojang.blaze3d.vertex.VertexConsumer;

/** Passes GUI vertices through unchanged and records position (scaled to pixels), UV and alpha. */
final class RecordingGuiVertexConsumer implements VertexConsumer {
	private final VertexConsumer delegate;
	private final GuiElement element;
	private final float scale;

	RecordingGuiVertexConsumer(VertexConsumer delegate, GuiElement element, float scale) {
		this.delegate = delegate;
		this.element = element;
		this.scale = scale;
	}

	@Override
	public VertexConsumer addVertex(float x, float y, float z) {
		element.addVertex(x * scale, y * scale);
		delegate.addVertex(x, y, z);
		return this;
	}

	@Override
	public void addVertex(float x, float y, float z, int color, float u, float v, int overlayCoords, int lightCoords, float nx, float ny, float nz) {
		element.addVertex(x * scale, y * scale);
		element.setUv(u, v);
		element.setAlpha((color >>> 24) / 255.0F);
		delegate.addVertex(x, y, z, color, u, v, overlayCoords, lightCoords, nx, ny, nz);
	}

	@Override
	public VertexConsumer setColor(int r, int g, int b, int a) {
		element.setAlpha(a / 255.0F);
		delegate.setColor(r, g, b, a);
		return this;
	}

	@Override
	public VertexConsumer setColor(int color) {
		element.setAlpha((color >>> 24) / 255.0F);
		delegate.setColor(color);
		return this;
	}

	@Override
	public VertexConsumer setUv(float u, float v) {
		element.setUv(u, v);
		delegate.setUv(u, v);
		return this;
	}

	@Override
	public VertexConsumer setUv1(int u, int v) {
		delegate.setUv1(u, v);
		return this;
	}

	@Override
	public VertexConsumer setUv2(int u, int v) {
		delegate.setUv2(u, v);
		return this;
	}

	@Override
	public VertexConsumer setUv3(float u, float v) {
		delegate.setUv3(u, v);
		return this;
	}

	@Override
	public VertexConsumer setNormal(float x, float y, float z) {
		delegate.setNormal(x, y, z);
		return this;
	}

	@Override
	public VertexConsumer setLineWidth(float width) {
		delegate.setLineWidth(width);
		return this;
	}
}
