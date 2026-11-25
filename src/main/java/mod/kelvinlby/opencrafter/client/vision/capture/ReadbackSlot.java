package mod.kelvinlby.opencrafter.client.vision.capture;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.commands.CommandEncoder;
import com.mojang.renderpearl.api.textures.GpuTexture;
import mod.kelvinlby.opencrafter.api.frame.StreamSpec;
import mod.kelvinlby.opencrafter.client.vision.VisionStream;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * One captured frame on its way from the GPU to shared memory: a set of mappable buffers
 * (one per stream) plus the frame's metadata. Copies are asynchronous; the slot becomes
 * ready when every copy's completion callback has run.
 */
final class ReadbackSlot implements AutoCloseable {
	private static final int USAGE = GpuBuffer.USAGE_MAP_READ | GpuBuffer.USAGE_COPY_DST;

	private final Map<VisionStream, GpuBuffer> buffers = new EnumMap<>(VisionStream.class);
	private int width;
	private int height;
	private int pendingCopies;
	private boolean inFlight;
	private @Nullable FrameMeta meta;

	boolean isFree() {
		return !inFlight;
	}

	boolean isReady() {
		return inFlight && pendingCopies == 0;
	}

	/** Makes sure the slot holds exactly one correctly sized buffer per requested stream. */
	void prepare(Set<VisionStream> streams, int width, int height) {
		if (this.width != width || this.height != height || !buffers.keySet().equals(streams)) {
			close();
			this.width = width;
			this.height = height;
			for (VisionStream stream : streams) {
				buffers.put(stream, createBuffer(stream, width, height));
			}
		}
	}

	void begin(FrameMeta frameMeta) {
		this.meta = frameMeta;
		this.inFlight = true;
		this.pendingCopies = 0;
	}

	void copy(VisionStream stream, GpuTexture source) {
		pendingCopies++;
		CommandEncoder encoder = RenderSystem.getDevice().createCommandEncoder();
		encoder.copyTextureToBuffer(source, buffers.get(stream), 0L, () -> pendingCopies--, 0);
	}

	void release() {
		inFlight = false;
		meta = null;
	}

	FrameMeta meta() {
		if (meta == null) {
			throw new IllegalStateException("Readback slot has no frame");
		}
		return meta;
	}

	GpuBuffer buffer(VisionStream stream) {
		return buffers.get(stream);
	}

	Set<VisionStream> streams() {
		return buffers.keySet();
	}

	List<StreamSpec> specs() {
		List<StreamSpec> specs = new ArrayList<>(buffers.size());
		for (VisionStream stream : buffers.keySet()) {
			specs.add(stream.spec(width, height));
		}
		return specs;
	}

	private static GpuBuffer createBuffer(VisionStream stream, int width, int height) {
		return RenderSystem.getDevice().createBuffer(() -> "open-crafter readback " + stream.wireName, USAGE, stream.byteSize(width, height));
	}

	@Override
	public void close() {
		buffers.values().forEach(GpuBuffer::close);
		buffers.clear();
	}
}
