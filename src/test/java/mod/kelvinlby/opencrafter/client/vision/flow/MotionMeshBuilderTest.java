package mod.kelvinlby.opencrafter.client.vision.flow;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import mod.kelvinlby.opencrafter.client.vision.record.FrameGeometry;
import mod.kelvinlby.opencrafter.client.vision.record.GeometryRecord;
import mod.kelvinlby.opencrafter.client.vision.record.RecordPhase;
import mod.kelvinlby.opencrafter.client.vision.record.SubmitKey;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MotionMeshBuilderTest {
	private static final SubmitKey ZOMBIE = new SubmitKey(RecordPhase.WORLD, new SubmitKey.EntityOwner(7), "model", 0);
	private static final SubmitKey HAND = new SubmitKey(RecordPhase.HAND, SubmitKey.HAND_OWNER, "model", 0);

	@Test
	void quadBecomesTwoTrianglesCarryingBothPositions() {
		FrameGeometry prev = frameWithQuad(ZOMBIE, 0.0F);
		FrameGeometry cur = frameWithQuad(ZOMBIE, 1.0F);
		FloatArrayList mesh = MotionMeshBuilder.build(prev, cur, RecordPhase.WORLD);
		assertEquals(6 * MotionMeshBuilder.FLOATS_PER_VERTEX, mesh.size());
		// second vertex of the first triangle is quad vertex 1: prev x = 1, cur x = 2, valid
		int v = MotionMeshBuilder.FLOATS_PER_VERTEX;
		assertEquals(1.0F, mesh.getFloat(v));
		assertEquals(2.0F, mesh.getFloat(v + 3));
		assertEquals(1.0F, mesh.getFloat(v + 6));
	}

	@Test
	void vanishedObjectIsMarkedInvalid() {
		FloatArrayList mesh = MotionMeshBuilder.build(frameWithQuad(ZOMBIE, 0.0F), new FrameGeometry(), RecordPhase.WORLD);
		assertEquals(0.0F, mesh.getFloat(MotionMeshBuilder.FLOATS_PER_VERTEX - 1));
	}

	@Test
	void changedShapeIsMarkedInvalid() {
		FrameGeometry cur = frameWithQuad(ZOMBIE, 0.0F);
		cur.recordFor(ZOMBIE).addVertex(9, 9, 9);
		FloatArrayList mesh = MotionMeshBuilder.build(frameWithQuad(ZOMBIE, 0.0F), cur, RecordPhase.WORLD);
		assertEquals(0.0F, mesh.getFloat(MotionMeshBuilder.FLOATS_PER_VERTEX - 1));
	}

	@Test
	void phasesAreSeparated() {
		FrameGeometry prev = frameWithQuad(HAND, 0.0F);
		assertEquals(0, MotionMeshBuilder.build(prev, prev, RecordPhase.WORLD).size());
		assertEquals(6 * MotionMeshBuilder.FLOATS_PER_VERTEX, MotionMeshBuilder.build(prev, prev, RecordPhase.HAND).size());
	}

	@Test
	void linesProduceNoTriangles() {
		FrameGeometry frame = new FrameGeometry();
		GeometryRecord record = frame.recordFor(ZOMBIE);
		record.beginSegment(PrimitiveTopology.LINES);
		record.addVertex(0, 0, 0);
		record.addVertex(1, 0, 0);
		assertEquals(0, MotionMeshBuilder.build(frame, frame, RecordPhase.WORLD).size());
	}

	private static FrameGeometry frameWithQuad(SubmitKey key, float offset) {
		FrameGeometry frame = new FrameGeometry();
		GeometryRecord record = frame.recordFor(key);
		record.beginSegment(PrimitiveTopology.QUADS);
		record.addVertex(offset, 0, -5);
		record.addVertex(offset + 1, 0, -5);
		record.addVertex(offset + 1, 1, -5);
		record.addVertex(offset, 1, -5);
		return frame;
	}
}
