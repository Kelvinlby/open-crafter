package mod.kelvinlby.opencrafter.api.frame;

import org.jspecify.annotations.Nullable;

/**
 * Adds one entry to the metadata of every captured frame. Called on the render thread at the
 * moment the frame is captured, so it can describe exactly the pixels being read back.
 */
public interface FrameAnnotator {
	/** The meta key this annotator fills, e.g. {@code "interactions"}. */
	String key();

	/** The value for the frame with this index and framebuffer size, or null to leave the key out. */
	@Nullable Object annotate(long frameIndex, int width, int height);
}
