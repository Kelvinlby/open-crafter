package mod.kelvinlby.opencrafter.api.frame;

/**
 * One image-shaped stream inside a frame, row-major, top row first, tightly packed:
 * {@code height * width * channels} elements of {@code dtype}.
 */
public record StreamSpec(String name, DType dtype, int channels, int width, int height) {
	public long byteSize() {
		return (long) width * height * channels * dtype.bytes;
	}
}
