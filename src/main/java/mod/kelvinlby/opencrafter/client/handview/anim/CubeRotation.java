package mod.kelvinlby.opencrafter.client.handview.anim;

import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.List;

/**
 * Finds how a block model was turned: the one of the 24 quarter-turn rotations of a cube (about its
 * centre) that lays every face of the unturned model onto a face of the turned one.
 */
public final class CubeRotation {
	/** One model face: where its centre is, which way it faces and what it is drawn with. */
	public record Face(Vector3fc centre, Vector3fc normal, Object material) {
	}

	private static final float EPSILON = 1e-3F;
	private static final Vector3f CENTRE = new Vector3f(0.5F);
	private static final List<Quaternionf> ROTATIONS = rotations();

	private CubeRotation() {
	}

	/** The rotation about the block centre taking {@code model} onto {@code turned}, or null if none does. */
	public static Matrix4f find(List<Face> model, List<Face> turned) {
		if (model.isEmpty() || model.size() != turned.size()) {
			return null;
		}
		for (Quaternionf rotation : ROTATIONS) {
			if (matches(model, turned, rotation)) {
				return new Matrix4f().translate(CENTRE).rotate(rotation).translate(new Vector3f(CENTRE).negate());
			}
		}
		return null;
	}

	private static boolean matches(List<Face> model, List<Face> turned, Quaternionf rotation) {
		for (Face face : model) {
			Vector3f centre = new Vector3f(face.centre()).sub(CENTRE).rotate(rotation).add(CENTRE);
			Vector3f normal = new Vector3f(face.normal()).rotate(rotation);
			if (!contains(turned, centre, normal, face.material())) {
				return false;
			}
		}
		return true;
	}

	private static boolean contains(List<Face> faces, Vector3f centre, Vector3f normal, Object material) {
		for (Face face : faces) {
			if (face.material().equals(material) && face.centre().distance(centre) < EPSILON && face.normal().distance(normal) < EPSILON) {
				return true;
			}
		}
		return false;
	}

	/** The identity first, so symmetric models keep their orientation. */
	private static List<Quaternionf> rotations() {
		List<Quaternionf> rotations = new ArrayList<>();
		for (int y = 0; y < 4; y++) {
			for (int x = 0; x < 4; x++) {
				rotations.add(quarterTurns(x, y, 0));
			}
			rotations.add(quarterTurns(0, y, 1));
			rotations.add(quarterTurns(0, y, 3));
		}
		return rotations;
	}

	private static Quaternionf quarterTurns(int x, int y, int z) {
		float quarter = (float) (Math.PI / 2);
		return new Quaternionf().rotateY(y * quarter).rotateX(x * quarter).rotateZ(z * quarter);
	}
}
