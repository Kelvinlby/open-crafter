package mod.kelvinlby.opencrafter.client.vision;

import com.mojang.blaze3d.pipeline.RenderTarget;
import mod.kelvinlby.opencrafter.client.vision.capture.VisionCapture;
import org.jspecify.annotations.Nullable;

/**
 * Static entry points for the vision mixins. Mixins stay one-liners and call into here;
 * everything is a no-op until {@link VisionModule} installed the capture.
 */
public final class VisionHooks {
	private static @Nullable VisionCapture capture;

	private VisionHooks() {
	}

	static void install(VisionCapture instance) {
		capture = instance;
	}

	public static void onFrameStart() {
		if (capture != null) {
			capture.onFrameStart();
		}
	}

	public static void onFrameRendered(RenderTarget mainTarget) {
		if (capture != null) {
			capture.onFrameRendered(mainTarget);
		}
	}
}
