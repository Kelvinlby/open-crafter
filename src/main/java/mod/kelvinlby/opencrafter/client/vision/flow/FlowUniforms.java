package mod.kelvinlby.opencrafter.client.vision.flow;

import com.mojang.blaze3d.buffers.Std140Builder;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import mod.kelvinlby.opencrafter.client.vision.gpu.VisionPipelines;
import org.joml.Matrix4d;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;

/** The {@code VisionFlow} uniform block (see shaders/include/vision_flow.glsl), uploaded once per capture. */
public final class FlowUniforms implements AutoCloseable {
	public static final String NAME = VisionPipelines.FLOW_UNIFORM_BLOCK;
	private static final int MATRICES = 10;
	private static final int VECTORS = 4;
	private static final int SIZE = MATRICES * 64 + VECTORS * 16;

	private final GpuBuffer buffer = RenderSystem.getDevice().createBuffer(() -> "open-crafter VisionFlow",
		GpuBuffer.USAGE_UNIFORM | GpuBuffer.USAGE_COPY_DST, SIZE);

	public GpuBuffer buffer() {
		return buffer;
	}

	public void upload(CameraSnapshot prev, CameraSnapshot cur, int width, int height, boolean zeroToOne) {
		try (MemoryStack stack = MemoryStack.stackPush()) {
			Std140Builder builder = Std140Builder.onStack(stack, SIZE);
			putMatrices(builder, prev, cur);
			builder.putVec4((float) (prev.x - cur.x), (float) (prev.y - cur.y), (float) (prev.z - cur.z), 0.0F);
			builder.putVec4(width, height, 1.0F / width, 1.0F / height);
			builder.putVec4(zeroToOne ? 1.0F : 0.0F, prev.clearDepth(zeroToOne), cur.clearDepth(zeroToOne), 0.0F);
			builder.putVec4(prev.levelRendered ? 1.0F : 0.0F, cur.levelRendered ? 1.0F : 0.0F, 0.0F, 0.0F);
			ByteBuffer data = builder.get();
			RenderSystem.getDevice().createCommandEncoder().writeToBuffer(buffer.slice(), data);
		}
	}

	private static void putMatrices(Std140Builder builder, CameraSnapshot prev, CameraSnapshot cur) {
		builder.putMat4f(prev.worldProjection);
		builder.putMat4f(inverse(prev.worldProjection));
		builder.putMat4f(inverse(prev.viewRotation));
		builder.putMat4f(cur.worldProjection);
		builder.putMat4f(inverse(cur.worldProjection));
		builder.putMat4f(cur.viewRotation);
		builder.putMat4f(prev.handProjection);
		builder.putMat4f(inverse(prev.handProjection));
		builder.putMat4f(cur.handProjection);
		builder.putMat4f(inverse(cur.handProjection));
	}

	/** Inverted in double precision: projections with a tiny near plane lose digits in float. */
	private static Matrix4f inverse(Matrix4fc matrix) {
		return new Matrix4f().set(new Matrix4d(matrix).invert());
	}

	@Override
	public void close() {
		buffer.close();
	}
}
