package mod.kelvinlby.opencrafter.transport.shm;

import mod.kelvinlby.opencrafter.api.frame.StreamSpec;

import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/** Where each stream of one frame lives inside a slot, and how large the slot must be. */
record SlotPlan(List<StreamSpec> streams, long[] offsets, long slotBytes) {
	/** @throws IllegalArgumentException for invalid or too many streams */
	static SlotPlan of(List<StreamSpec> streams) {
		validate(streams);
		long[] offsets = new long[streams.size()];
		long cursor = RingLayout.SLOT_HEADER_SIZE;
		for (int i = 0; i < offsets.length; i++) {
			offsets[i] = cursor;
			cursor = RingLayout.alignUp(cursor + streams.get(i).byteSize(), RingLayout.DATA_ALIGNMENT);
		}
		return new SlotPlan(List.copyOf(streams), offsets, RingLayout.alignUp(cursor, RingLayout.SLOT_ALIGNMENT));
	}

	int indexOf(String name) {
		for (int i = 0; i < streams.size(); i++) {
			if (streams.get(i).name().equals(name)) {
				return i;
			}
		}
		return -1;
	}

	static byte[] nameBytes(StreamSpec spec) {
		return spec.name().getBytes(StandardCharsets.UTF_8);
	}

	static byte[] dtypeBytes(StreamSpec spec) {
		return ("<" + spec.dtype().numpy).getBytes(StandardCharsets.US_ASCII);
	}

	private static void validate(List<StreamSpec> streams) {
		if (streams.size() > RingLayout.MAX_STREAMS) {
			throw new IllegalArgumentException("At most " + RingLayout.MAX_STREAMS + " streams per frame");
		}
		Set<String> names = new HashSet<>();
		for (StreamSpec spec : streams) {
			validate(spec);
			if (!names.add(spec.name())) {
				throw new IllegalArgumentException("Duplicate stream: " + spec.name());
			}
		}
	}

	private static void validate(StreamSpec spec) {
		if (spec.name() == null || spec.dtype() == null) {
			throw new IllegalArgumentException("Stream name and dtype are required");
		}
		int nameLength = nameBytes(spec).length;
		if (nameLength == 0 || nameLength >= RingLayout.NAME_BYTES) {
			throw new IllegalArgumentException("Stream name must be 1.." + (RingLayout.NAME_BYTES - 1) + " UTF-8 bytes: " + spec.name());
		}
		if (spec.width() <= 0 || spec.height() <= 0 || spec.channels() <= 0) {
			throw new IllegalArgumentException("Stream " + spec.name() + " needs positive width, height and channels");
		}
		if (spec.byteSize() > Integer.MAX_VALUE) {
			throw new IllegalArgumentException("Stream " + spec.name() + " exceeds 2 GiB");
		}
	}
}
