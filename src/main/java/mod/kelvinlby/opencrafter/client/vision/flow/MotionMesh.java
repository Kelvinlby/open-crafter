package mod.kelvinlby.opencrafter.client.vision.flow;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** A reusable GPU vertex buffer holding one motion mesh; grows, never shrinks. */
final class MotionMesh implements AutoCloseable {
	private static final int BYTES_PER_VERTEX = MotionMeshBuilder.FLOATS_PER_VERTEX * Float.BYTES;

	private final String label;
	private @Nullable GpuBuffer buffer;
	private ByteBuffer staging = ByteBuffer.allocateDirect(0);
	private int vertexCount;

	MotionMesh(String label) {
		this.label = label;
	}

	void upload(FloatArrayList floats) {
		vertexCount = floats.size() / MotionMeshBuilder.FLOATS_PER_VERTEX;
		if (vertexCount == 0) {
			return;
		}
		long bytes = (long) vertexCount * BYTES_PER_VERTEX;
		ensureCapacity(bytes);
		RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(0, bytes), fill(floats, (int) bytes));
	}

	int vertexCount() {
		return vertexCount;
	}

	GpuBufferSlice slice() {
		return buffer.slice(0, (long) vertexCount * BYTES_PER_VERTEX);
	}

	private ByteBuffer fill(FloatArrayList floats, int bytes) {
		if (staging.capacity() < bytes) {
			staging = ByteBuffer.allocateDirect(bytes * 2).order(ByteOrder.nativeOrder());
		}
		staging.clear();
		staging.asFloatBuffer().put(floats.elements(), 0, floats.size());
		return staging.limit(bytes);
	}

	private void ensureCapacity(long bytes) {
		if (buffer != null && buffer.size() >= bytes) {
			return;
		}
		close();
		long size = Math.max(bytes * 2, 64 * 1024);
		buffer = RenderSystem.getDevice().createBuffer(() -> "open-crafter " + label, GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, size);
	}

	@Override
	public void close() {
		if (buffer != null) {
			buffer.close();
			buffer = null;
		}
	}
}
