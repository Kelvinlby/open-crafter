package mod.kelvinlby.opencrafter.client.vision.gui;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import it.unimi.dsi.fastutil.floats.FloatArrayList;

/**
 * Triangles of GUI elements for the GUI flow passes. Per vertex: position in the reference
 * frame, position in the current frame, UV, alpha, the current clip rectangle and a "vanished"
 * flag ({@link #FLOATS_PER_VERTEX} floats, layout of {@code VisionPipelines.GUI_MOTION_FORMAT}).
 */
final class GuiMotionMesh {
	static final int FLOATS_PER_VERTEX = 12;
	private static final int[] QUAD_TRIANGLES = {0, 1, 2, 0, 2, 3};
	private static final float[] NO_CLIP = {-1.0e9F, -1.0e9F, 1.0e9F, 1.0e9F};

	private GuiMotionMesh() {
	}

	/** {@code prev}'s triangles moving to {@code cur}'s vertices, clipped by {@code cur}'s scissor. */
	static void addMoved(FloatArrayList out, GuiElement prev, GuiElement cur) {
		addTriangles(out, prev, cur, cur.clip, 0.0F);
	}

	/** An element that is gone in the current frame: its pixels are occluded. */
	static void addVanished(FloatArrayList out, GuiElement prev) {
		addTriangles(out, prev, prev, NO_CLIP, 1.0F);
	}

	/** An element of the current frame, for the coverage pass (only the current position is used). */
	static void addCovering(FloatArrayList out, GuiElement cur) {
		addTriangles(out, cur, cur, NO_CLIP, 0.0F);
	}

	static boolean supported(GuiElement element) {
		return element.topology == PrimitiveTopology.QUADS || element.topology == PrimitiveTopology.TRIANGLES;
	}

	private static void addTriangles(FloatArrayList out, GuiElement prev, GuiElement cur, float[] clip, float vanished) {
		int count = prev.vertexCount();
		if (prev.topology == PrimitiveTopology.QUADS) {
			for (int quad = 0; quad + 3 < count; quad += 4) {
				for (int corner : QUAD_TRIANGLES) {
					addVertex(out, prev, cur, quad + corner, clip, vanished);
				}
			}
		} else {
			for (int i = 0; i + 2 < count; i += 3) {
				for (int corner = 0; corner < 3; corner++) {
					addVertex(out, prev, cur, i + corner, clip, vanished);
				}
			}
		}
	}

	private static void addVertex(FloatArrayList out, GuiElement prev, GuiElement cur, int i, float[] clip, float vanished) {
		out.add(prev.positions.getFloat(2 * i));
		out.add(prev.positions.getFloat(2 * i + 1));
		out.add(cur.positions.getFloat(2 * i));
		out.add(cur.positions.getFloat(2 * i + 1));
		out.add(prev.uvs.getFloat(2 * i));
		out.add(prev.uvs.getFloat(2 * i + 1));
		out.add(prev.alphas.getFloat(i));
		out.add(clip[0]);
		out.add(clip[1]);
		out.add(clip[2]);
		out.add(clip[3]);
		out.add(vanished);
	}
}
