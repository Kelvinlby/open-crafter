package mod.kelvinlby.opencrafter.transport.shm;

import mod.kelvinlby.opencrafter.api.frame.FrameSlot;
import mod.kelvinlby.opencrafter.api.frame.StreamSpec;

import java.nio.ByteBuffer;
import java.util.Map;

/** A slot handed out by {@link FrameRing#acquire}; all state changes go through the ring. */
final class RingSlot implements FrameSlot {
	final FrameRing ring;
	final RingMapping mapping;
	final int index;
	final long base;
	final SlotPlan plan;
	/** Guarded by the ring's lock. */
	boolean finished;

	RingSlot(FrameRing ring, RingMapping mapping, int index, SlotPlan plan) {
		this.ring = ring;
		this.mapping = mapping;
		this.index = index;
		this.base = mapping.slotBase(index);
		this.plan = plan;
	}

	@Override
	public ByteBuffer stream(String name) {
		int stream = plan.indexOf(name);
		if (stream < 0) {
			throw new IllegalArgumentException("Stream not in this frame: " + name);
		}
		StreamSpec spec = plan.streams().get(stream);
		return mapping.buffer(base + plan.offsets()[stream], spec.byteSize());
	}

	@Override
	public void setMeta(Map<String, Object> meta) {
		ring.writeMeta(this, meta);
	}

	@Override
	public void commit(long frameIndex, long captureNanos) {
		ring.commit(this, frameIndex, captureNanos);
	}

	@Override
	public void abort() {
		ring.abort(this);
	}
}
