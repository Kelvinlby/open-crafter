package mod.kelvinlby.opencrafter.client.handview.anim;

import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CubeRotationTest {
	/** A step-like model: a bottom half plus a back quarter, each face with its own texture. */
	private static final List<CubeRotation.Face> STAIR = List.of(
		face(0.5F, 0, 0.5F, 0, -1, 0, "bottom"),
		face(0.5F, 0.5F, 0.75F, 0, 1, 0, "tread"),
		face(0.5F, 1, 0.25F, 0, 1, 0, "top"),
		face(0.5F, 0.75F, 0.5F, 0, 0, 1, "riser"),
		face(0.5F, 0.5F, 0, 0, 0, -1, "back"));

	@Test
	void findsTheIdentityForAnUnturnedModel() {
		Matrix4f rotation = CubeRotation.find(STAIR, STAIR);
		assertNotNull(rotation);
		assertTrue(rotation.equals(new Matrix4f(), 1e-5F));
	}

	@Test
	void findsAQuarterTurn() {
		Matrix4f turn = new Matrix4f().translate(0.5F, 0.5F, 0.5F).rotateY((float) (Math.PI / 2)).translate(-0.5F, -0.5F, -0.5F);
		Matrix4f found = CubeRotation.find(STAIR, turned(STAIR, turn));
		assertNotNull(found);
		assertTrue(found.equals(turn, 1e-5F));
	}

	@Test
	void findsAnUpsideDownTurn() {
		Matrix4f turn = new Matrix4f().translate(0.5F, 0.5F, 0.5F).rotateX((float) Math.PI).rotateY((float) Math.PI / 2).translate(-0.5F, -0.5F, -0.5F);
		assertNotNull(CubeRotation.find(STAIR, turned(STAIR, turn)));
	}

	@Test
	void rejectsADifferentShape() {
		List<CubeRotation.Face> other = new ArrayList<>(STAIR);
		other.set(1, face(0.5F, 0.4F, 0.75F, 0, 1, 0, "tread"));
		assertNull(CubeRotation.find(STAIR, other));
	}

	private static List<CubeRotation.Face> turned(List<CubeRotation.Face> faces, Matrix4f turn) {
		List<CubeRotation.Face> out = new ArrayList<>();
		for (CubeRotation.Face f : faces) {
			out.add(new CubeRotation.Face(turn.transformPosition(new Vector3f(f.centre())), turn.transformDirection(new Vector3f(f.normal())), f.material()));
		}
		return out;
	}

	private static CubeRotation.Face face(float x, float y, float z, float nx, float ny, float nz, String material) {
		return new CubeRotation.Face(new Vector3f(x, y, z), new Vector3f(nx, ny, nz), material);
	}
}
