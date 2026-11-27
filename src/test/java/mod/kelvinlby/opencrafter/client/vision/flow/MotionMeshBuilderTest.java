package mod.kelvinlby.opencrafter.client.vision.flow;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import mod.kelvinlby.opencrafter.client.vision.record.FrameGeometry;
import mod.kelvinlby.opencrafter.client.vision.record.GeometryRecord;
import mod.kelvinlby.opencrafter.client.vision.record.RecordPhase;
import mod.kelvinlby.opencrafter.client.vision.record.SubmitKey;
import net.minecraft.world.InteractionHand;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MotionMeshBuilderTest {
	private static final SubmitKey ZOMBIE = new SubmitKey(RecordPhase.WORLD, new SubmitKey.EntityOwner(7), "model", 0);
	private static final SubmitKey HAND = new SubmitKey(RecordPhase.HAND, SubmitKey.HAND_OWNER, "model", 0);
	private static final SubmitKey MAIN_HAND_ITEM = new SubmitKey(RecordPhase.HAND, new SubmitKey.HandOwner(InteractionHand.MAIN_HAND), "item", 0);
	private static final Object FLIGHT = new Object();
	private static final SubmitKey FLIGHT_ITEM = MAIN_HAND_ITEM.withOwner(FLIGHT);

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
	void successorCarriesOnAVanishedHeldItem() {
		FrameGeometry cur = frameWithQuad(FLIGHT_ITEM, 1.0F);
		cur.noteSuccessor(MAIN_HAND_ITEM.owner(), FLIGHT);
		FloatArrayList mesh = MotionMeshBuilder.build(frameWithQuad(MAIN_HAND_ITEM, 0.0F), cur, RecordPhase.HAND);
		assertEquals(MotionMeshBuilder.MATCHED, mesh.getFloat(MotionMeshBuilder.FLOATS_PER_VERTEX - 1));
		assertEquals(1.0F, mesh.getFloat(3));
	}

	@Test
	void successorIsIgnoredWhileTheHeldItemStays() {
		FrameGeometry cur = frameWithQuad(MAIN_HAND_ITEM, 0.5F);
		addQuad(cur, FLIGHT_ITEM, 1.0F);
		cur.noteSuccessor(MAIN_HAND_ITEM.owner(), FLIGHT);
		FloatArrayList mesh = MotionMeshBuilder.build(frameWithQuad(MAIN_HAND_ITEM, 0.0F), cur, RecordPhase.HAND);
		assertEquals(6 * MotionMeshBuilder.FLOATS_PER_VERTEX, mesh.size());
		assertEquals(0.5F, mesh.getFloat(3));
	}

	@Test
	void successorOnlyTakesOverWhenItIsNew() {
		// the flight was already drawn in the reference frame, so a held item vanishing now is not it leaving the hand
		FrameGeometry prev = frameWithQuad(MAIN_HAND_ITEM, 0.0F);
		prev.recordFor(new SubmitKey(RecordPhase.HAND, FLIGHT, "other", 0));
		FrameGeometry cur = frameWithQuad(FLIGHT_ITEM, 1.0F);
		cur.noteSuccessor(MAIN_HAND_ITEM.owner(), FLIGHT);
		FloatArrayList mesh = MotionMeshBuilder.build(prev, cur, RecordPhase.HAND);
		assertEquals(6 * MotionMeshBuilder.FLOATS_PER_VERTEX, mesh.size());
		assertEquals(MotionMeshBuilder.VANISHED, mesh.getFloat(MotionMeshBuilder.FLOATS_PER_VERTEX - 1));
	}

	@Test
	void settledObjectThatVanishedFollowsTheWorld() {
		FrameGeometry prev = frameWithQuad(FLIGHT_ITEM, 0.0F);
		prev.noteSettled(FLIGHT);
		FloatArrayList mesh = MotionMeshBuilder.build(prev, new FrameGeometry(), RecordPhase.HAND);
		assertEquals(MotionMeshBuilder.SETTLED, mesh.getFloat(MotionMeshBuilder.FLOATS_PER_VERTEX - 1));
	}

	@Test
	void settledObjectStillDrawnIsMatched() {
		FrameGeometry prev = frameWithQuad(FLIGHT_ITEM, 0.0F);
		prev.noteSettled(FLIGHT);
		FloatArrayList mesh = MotionMeshBuilder.build(prev, frameWithQuad(FLIGHT_ITEM, 0.0F), RecordPhase.HAND);
		assertEquals(MotionMeshBuilder.MATCHED, mesh.getFloat(MotionMeshBuilder.FLOATS_PER_VERTEX - 1));
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
		addQuad(frame, key, offset);
		return frame;
	}

	private static void addQuad(FrameGeometry frame, SubmitKey key, float offset) {
		GeometryRecord record = frame.recordFor(key);
		record.beginSegment(PrimitiveTopology.QUADS);
		record.addVertex(offset, 0, -5);
		record.addVertex(offset + 1, 0, -5);
		record.addVertex(offset + 1, 1, -5);
		record.addVertex(offset, 1, -5);
	}
}
