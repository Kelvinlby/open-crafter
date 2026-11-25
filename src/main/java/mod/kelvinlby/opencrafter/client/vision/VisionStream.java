package mod.kelvinlby.opencrafter.client.vision;

import mod.kelvinlby.opencrafter.api.frame.DType;
import mod.kelvinlby.opencrafter.api.frame.StreamSpec;

import java.util.Arrays;
import java.util.Locale;

/** The image streams the vision sensor can publish (see docs/ARCHITECTURE.md, "Vision streams"). */
public enum VisionStream {
	RGB("rgb", DType.UINT8, 4),
	FLOW("flow", DType.FLOAT32, 2),
	FLOW_OCCLUDED("flow_occluded", DType.UINT8, 1);

	public final String wireName;
	public final DType dtype;
	public final int channels;

	VisionStream(String wireName, DType dtype, int channels) {
		this.wireName = wireName;
		this.dtype = dtype;
		this.channels = channels;
	}

	public StreamSpec spec(int width, int height) {
		return new StreamSpec(wireName, dtype, channels, width, height);
	}

	public long byteSize(int width, int height) {
		return (long) width * height * channels * dtype.bytes;
	}

	/** True for the streams produced by the optical-flow passes. */
	public boolean isFlow() {
		return this != RGB;
	}

	public static VisionStream byWireName(String name) {
		String normalized = name.toLowerCase(Locale.ROOT);
		return Arrays.stream(values())
			.filter(stream -> stream.wireName.equals(normalized))
			.findFirst()
			.orElseThrow(() -> new IllegalArgumentException("Unknown vision stream: " + name));
	}
}
