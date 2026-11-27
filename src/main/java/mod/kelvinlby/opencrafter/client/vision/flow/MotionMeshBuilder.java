package mod.kelvinlby.opencrafter.client.vision.flow;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import mod.kelvinlby.opencrafter.client.vision.record.FrameGeometry;
import mod.kelvinlby.opencrafter.client.vision.record.GeometryRecord;
import mod.kelvinlby.opencrafter.client.vision.record.RecordPhase;
import mod.kelvinlby.opencrafter.client.vision.record.SubmitKey;
import org.jspecify.annotations.Nullable;

/**
 * Pairs the reference frame's recorded geometry with the current frame's and emits a triangle
 * list with, per vertex, the reference view-space position and the current one. Objects that
 * vanished or changed shape get {@code w = 0} (their pixels become occluded); objects that
 * vanished from on top of world geometry get {@code w = 2} (their pixels follow the world).
 * An object a successor took over is paired with the successor. Pure CPU logic.
 */
public final class MotionMeshBuilder {
	/** prev xyz, cur xyz, cur state ({@link #VANISHED}, {@link #MATCHED} or {@link #SETTLED}). */
	public static final int FLOATS_PER_VERTEX = 7;
	public static final float VANISHED = 0.0F;
	public static final float MATCHED = 1.0F;
	public static final float SETTLED = 2.0F;

	private MotionMeshBuilder() {
	}

	public static FloatArrayList build(FrameGeometry prev, @Nullable FrameGeometry cur, RecordPhase phase) {
		FloatArrayList out = new FloatArrayList();
		prev.forEach((key, prevRecord) -> {
			if (prevRecord.phase() == phase) {
				GeometryRecord curRecord = counterpart(prev, cur, key, prevRecord);
				float state = curRecord != null ? MATCHED : prev.isSettled(key.owner()) ? SETTLED : VANISHED;
				appendRecord(out, prevRecord, curRecord, state);
			}
		});
		return out;
	}

	/** The same surface in the current frame: same key, else the successor's, if it has the same shape. */
	private static @Nullable GeometryRecord counterpart(FrameGeometry prev, @Nullable FrameGeometry cur, SubmitKey key, GeometryRecord prevRecord) {
		if (cur == null) {
			return null;
		}
		GeometryRecord same = cur.get(key);
		if (same != null) {
			return prevRecord.sameShape(same) ? same : null;
		}
		Object successor = cur.successorOf(key.owner());
		if (successor == null || prev.hasOwner(successor)) {
			return null;
		}
		GeometryRecord taken = cur.get(key.withOwner(successor));
		return taken != null && prevRecord.sameShape(taken) ? taken : null;
	}

	private static void appendRecord(FloatArrayList out, GeometryRecord prev, @Nullable GeometryRecord cur, float state) {
		for (int segment = 0; segment < prev.segmentCount(); segment++) {
			appendSegment(out, prev, cur, state, prev.topology(segment), prev.segmentStart(segment), prev.segmentEnd(segment));
		}
	}

	private static void appendSegment(FloatArrayList out, GeometryRecord prev, @Nullable GeometryRecord cur, float state, PrimitiveTopology topology, int start, int end) {
		switch (topology) {
			case QUADS -> {
				for (int q = start; q + 3 < end; q += 4) {
					appendTriangle(out, prev, cur, state, q, q + 1, q + 2);
					appendTriangle(out, prev, cur, state, q, q + 2, q + 3);
				}
			}
			case TRIANGLES -> {
				for (int t = start; t + 2 < end; t += 3) {
					appendTriangle(out, prev, cur, state, t, t + 1, t + 2);
				}
			}
			case TRIANGLE_STRIP -> {
				for (int i = start; i + 2 < end; i++) {
					appendTriangle(out, prev, cur, state, i, i + 1, i + 2);
				}
			}
			case TRIANGLE_FAN -> {
				for (int i = start + 1; i + 1 < end; i++) {
					appendTriangle(out, prev, cur, state, start, i, i + 1);
				}
			}
			default -> {
				// Lines and points have no area: nothing to rasterise.
			}
		}
	}

	private static void appendTriangle(FloatArrayList out, GeometryRecord prev, @Nullable GeometryRecord cur, float state, int a, int b, int c) {
		appendVertex(out, prev, cur, state, a);
		appendVertex(out, prev, cur, state, b);
		appendVertex(out, prev, cur, state, c);
	}

	private static void appendVertex(FloatArrayList out, GeometryRecord prev, @Nullable GeometryRecord cur, float state, int vertex) {
		for (int axis = 0; axis < 3; axis++) {
			out.add(prev.coordinate(vertex, axis));
		}
		for (int axis = 0; axis < 3; axis++) {
			out.add(cur == null ? 0.0F : cur.coordinate(vertex, axis));
		}
		out.add(state);
	}
}
