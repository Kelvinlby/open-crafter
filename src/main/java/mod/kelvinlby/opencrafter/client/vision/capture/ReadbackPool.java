package mod.kelvinlby.opencrafter.client.vision.capture;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.commands.GpuFence;
import mod.kelvinlby.opencrafter.client.vision.VisionStream;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;

/**
 * A small pool of readback slots so capture N+1 can be issued before frame N has been
 * mapped. Never waits on its own: when every slot is still in flight the caller drops the frame,
 * unless it asks for {@link #awaitInFlight()} (lossless consumers only).
 */
final class ReadbackPool implements AutoCloseable {
	private final List<ReadbackSlot> slots = new ArrayList<>();

	ReadbackPool(int size) {
		for (int i = 0; i < size; i++) {
			slots.add(new ReadbackSlot());
		}
	}

	@Nullable ReadbackSlot acquire(Set<VisionStream> streams, int width, int height) {
		for (ReadbackSlot slot : slots) {
			if (slot.isFree()) {
				slot.prepare(streams, width, height);
				return slot;
			}
		}
		return null;
	}

	/** Hands every completed slot to {@code consumer} in frame order, then frees it. */
	void drainReady(Consumer<ReadbackSlot> consumer) {
		List<ReadbackSlot> ready = slots.stream()
			.filter(ReadbackSlot::isReady)
			.sorted(Comparator.comparingLong(slot -> slot.meta().frameIndex()))
			.toList();
		for (ReadbackSlot slot : ready) {
			try {
				consumer.accept(slot);
			} finally {
				slot.release();
			}
		}
	}

	/**
	 * Blocks until the GPU finished every copy issued so far and runs their completion callbacks,
	 * so all in-flight slots become ready. Only for lossless consumers: it stalls the frame.
	 */
	void awaitInFlight() {
		try (GpuFence fence = RenderSystem.getDevice().createCommandEncoder().createFence()) {
			fence.awaitCompletion(GpuFence.NO_TIMEOUT);
		}
		RenderSystem.executePendingTasks();
	}

	@Override
	public void close() {
		slots.forEach(ReadbackSlot::close);
	}
}
