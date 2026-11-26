package mod.kelvinlby.opencrafter.client.handview.anim;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PoseBlendTest {
	private static final Matrix4f FROM = new Matrix4f().translate(0.5F, -0.5F, -0.7F).rotateY(0.8F).scale(0.4F);
	private static final Matrix4f TO = new Matrix4f().translate(2, 1, -4).rotateX(-0.3F).scale(1, 0.5F, 1);

	@Test
	void reproducesBothEnds() {
		assertTrue(PoseBlend.blend(FROM, TO, 0).equals(FROM, 1e-5F));
		assertTrue(PoseBlend.blend(FROM, TO, 1).equals(TO, 1e-5F));
	}

	@Test
	void keepsShearedStartExact() {
		Matrix4f sheared = new Matrix4f().scale(1.2F, 1, 1).mul(FROM);
		assertTrue(PoseBlend.blend(sheared, TO, 0).equals(sheared, 1e-5F));
		assertTrue(PoseBlend.blend(sheared, TO, 1).equals(TO, 1e-5F));
	}

	@Test
	void thePivotTravelsInAStraightLine() {
		Vector3f pivot = new Vector3f(0.5F);
		Vector3f start = FROM.transformPosition(new Vector3f(pivot));
		Vector3f end = TO.transformPosition(new Vector3f(pivot));
		Vector3f middle = PoseBlend.blend(FROM, TO, 0.5F, pivot).transformPosition(new Vector3f(pivot));
		assertTrue(middle.distance(new Vector3f(start).lerp(end, 0.5F)) < 1e-4, middle.toString());
		assertTrue(PoseBlend.blend(FROM, TO, 1, pivot).equals(TO, 1e-5F));
	}

	@Test
	void growsAndTravelsHalfwayAtTheMiddle() {
		Matrix4f middle = PoseBlend.blend(FROM, TO, 0.5F);
		Vector3f translation = middle.getTranslation(new Vector3f());
		assertEquals(1.25F, translation.x, 1e-4);
		assertEquals(0.7F, middle.getScale(new Vector3f()).x, 1e-4);
	}
}
