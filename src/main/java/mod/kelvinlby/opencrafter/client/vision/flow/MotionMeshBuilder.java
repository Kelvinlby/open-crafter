package mod.kelvinlby.opencrafter.client.vision.flow;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import mod.kelvinlby.opencrafter.client.vision.record.FrameGeometry;
import mod.kelvinlby.opencrafter.client.vision.record.GeometryRecord;
import mod.kelvinlby.opencrafter.client.vision.record.RecordPhase;
import org.jspecify.annotations.Nullable;

/**
 * Pairs the reference frame's recorded geometry with the current frame's and emits a triangle
 * list with, per vertex, the reference view-space position and the current one. Objects that
 * vanished or changed shape get {@code w = 0} (their pixels become occluded). Pure CPU logic.
 */
public final class MotionMeshBuilder {
	/** prev xyz, cur xyz, cur valid flag. */
	public static final int FLOATS_PER_VERTEX = 7;

	private MotionMeshBuilder() {
	}

	public static FloatArrayList build(FrameGeometry prev, @Nullable FrameGeometry cur, RecordPhase phase) {
		FloatArrayList out = new FloatArrayList();
		prev.forEach((key, prevRecord) -> {
			if (prevRecord.phase() == phase) {
				GeometryRecord curRecord = cur == null ? null : cur.get(key);
				boolean matched = curRecord != null && prevRecord.sameShape(curRecord);
				appendRecord(out, prevRecord, matched ? curRecord : null);
			}
		});
		return out;
	}

	private static void appendRecord(FloatArrayList out, GeometryRecord prev, @Nullable GeometryRecord cur) {
		for (int segment = 0; segment < prev.segmentCount(); segment++) {
			appendSegment(out, prev, cur, prev.topology(segment), prev.segmentStart(segment), prev.segmentEnd(segment));
		}
	}

	private static void appendSegment(FloatArrayList out, GeometryRecord prev, @Nullable GeometryRecord cur, PrimitiveTopology topology, int start, int end) {
		switch (topology) {
			case QUADS -> {
				for (int q = start; q + 3 < end; q += 4) {
					appendTriangle(out, prev, cur, q, q + 1, q + 2);
					appendTriangle(out, prev, cur, q, q + 2, q + 3);
				}
			}
			case TRIANGLES -> {
				for (int t = start; t + 2 < end; t += 3) {
					appendTriangle(out, prev, cur, t, t + 1, t + 2);
				}
			}
			case TRIANGLE_STRIP -> {
				for (int i = start; i + 2 < end; i++) {
					appendTriangle(out, prev, cur, i, i + 1, i + 2);
				}
			}
			case TRIANGLE_FAN -> {
				for (int i = start + 1; i + 1 < end; i++) {
					appendTriangle(out, prev, cur, start, i, i + 1);
				}
			}
			default -> {
				// Lines and points have no area: nothing to rasterise.
			}
		}
	}

	private static void appendTriangle(FloatArrayList out, GeometryRecord prev, @Nullable GeometryRecord cur, int a, int b, int c) {
		appendVertex(out, prev, cur, a);
		appendVertex(out, prev, cur, b);
		appendVertex(out, prev, cur, c);
	}

	private static void appendVertex(FloatArrayList out, GeometryRecord prev, @Nullable GeometryRecord cur, int vertex) {
		for (int axis = 0; axis < 3; axis++) {
			out.add(prev.coordinate(vertex, axis));
		}
		for (int axis = 0; axis < 3; axis++) {
			out.add(cur == null ? 0.0F : cur.coordinate(vertex, axis));
		}
		out.add(cur == null ? 0.0F : 1.0F);
	}
}
