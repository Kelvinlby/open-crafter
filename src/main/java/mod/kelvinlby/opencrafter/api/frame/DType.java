package mod.kelvinlby.opencrafter.api.frame;

/** Element type of a frame stream, mirrored by the backend (numpy dtype names). */
public enum DType {
	UINT8(1, "u1"),
	UINT16(2, "u2"),
	FLOAT16(2, "f2"),
	FLOAT32(4, "f4");

	public final int bytes;
	public final String numpy;

	DType(int bytes, String numpy) {
		this.bytes = bytes;
		this.numpy = numpy;
	}
}
