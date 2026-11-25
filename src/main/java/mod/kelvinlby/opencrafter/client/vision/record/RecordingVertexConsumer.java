package mod.kelvinlby.opencrafter.client.vision.record;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

/**
 * Passes every vertex through to the real buffer unchanged and additionally records its
 * view-space position (model-view matrix applied, exactly as the vertex shader does).
 */
final class RecordingVertexConsumer implements VertexConsumer {
	private final VertexConsumer delegate;
	private final Matrix4fc modelView;
	private final Vector3f scratch = new Vector3f();
	private final RecordSelector selector;

	/** Chooses the record each vertex goes to; {@code null} drops the vertex from recording. */
	@FunctionalInterface
	interface RecordSelector {
		GeometryRecord nextVertex();
	}

	RecordingVertexConsumer(VertexConsumer delegate, Matrix4fc modelView, RecordSelector selector) {
		this.delegate = delegate;
		this.modelView = modelView;
		this.selector = selector;
	}

	private void record(float x, float y, float z) {
		GeometryRecord record = selector.nextVertex();
		if (record != null) {
			modelView.transformPosition(x, y, z, scratch);
			record.addVertex(scratch.x, scratch.y, scratch.z);
		}
	}

	@Override
	public VertexConsumer addVertex(float x, float y, float z) {
		record(x, y, z);
		delegate.addVertex(x, y, z);
		return this;
	}

	@Override
	public void addVertex(float x, float y, float z, int color, float u, float v, int overlayCoords, int lightCoords, float nx, float ny, float nz) {
		record(x, y, z);
		delegate.addVertex(x, y, z, color, u, v, overlayCoords, lightCoords, nx, ny, nz);
	}

	@Override
	public VertexConsumer setColor(int r, int g, int b, int a) {
		delegate.setColor(r, g, b, a);
		return this;
	}

	@Override
	public VertexConsumer setColor(int color) {
		delegate.setColor(color);
		return this;
	}

	@Override
	public VertexConsumer setUv(float u, float v) {
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
