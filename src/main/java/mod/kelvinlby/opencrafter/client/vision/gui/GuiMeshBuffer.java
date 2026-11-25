package mod.kelvinlby.opencrafter.client.vision.gui;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import org.jspecify.annotations.Nullable;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/** A reusable GPU vertex buffer for one GUI motion mesh; grows, never shrinks. */
final class GuiMeshBuffer implements AutoCloseable {
	private static final int BYTES_PER_VERTEX = GuiMotionMesh.FLOATS_PER_VERTEX * Float.BYTES;

	private final String label;
	private @Nullable GpuBuffer buffer;
	private ByteBuffer staging = ByteBuffer.allocateDirect(0);

	GuiMeshBuffer(String label) {
		this.label = label;
	}

	void upload(FloatArrayList floats) {
		int bytes = floats.size() * Float.BYTES;
		if (bytes == 0) {
			return;
		}
		ensureCapacity(bytes);
		RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(0, bytes), fill(floats, bytes));
	}

	GpuBufferSlice slice(int firstVertex, int vertexCount) {
		return buffer.slice((long) firstVertex * BYTES_PER_VERTEX, (long) vertexCount * BYTES_PER_VERTEX);
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
		buffer = RenderSystem.getDevice().createBuffer(() -> "open-crafter " + label,
			GpuBuffer.USAGE_VERTEX | GpuBuffer.USAGE_COPY_DST, Math.max(bytes * 2, 64 * 1024));
	}

	@Override
	public void close() {
		if (buffer != null) {
			buffer.close();
			buffer = null;
		}
	}
}
