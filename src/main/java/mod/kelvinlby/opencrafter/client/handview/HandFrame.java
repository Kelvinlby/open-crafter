package mod.kelvinlby.opencrafter.client.handview;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import org.joml.Matrix4f;

/**
 * The first-person hand pass's frame as it begins: its starting pose and the model-view matrix the
 * hands are drawn with. Vanilla starts from the inverse view rotation (undone by the model-view) plus
 * view bobbing; shader mods such as Iris start from identity and move bobbing into the projection.
 */
final class HandFrame {
	private final Matrix4f pose = new Matrix4f();
	private final Matrix4f modelView = new Matrix4f();

	/** At the start of every hand pass (render thread). */
	void begin(PoseStack start) {
		pose.set(start.last().pose());
		modelView.set(RenderSystem.getModelViewMatrixCopy());
	}

	/** Hand pose space to camera space. */
	Matrix4f modelView() {
		return new Matrix4f(modelView);
	}

	/** The view bobbing the pass starts with, in camera space (identity when it lives in the projection). */
	Matrix4f bob() {
		return new Matrix4f(modelView).mul(pose);
	}
}
