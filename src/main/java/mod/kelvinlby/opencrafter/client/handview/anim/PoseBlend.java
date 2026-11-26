package mod.kelvinlby.opencrafter.client.handview.anim;

import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Blends two placements of a model (translation, rotation and per-axis scale) so that it moves,
 * turns and resizes along the way instead of fading. Both ends are reproduced exactly, even when
 * {@code from} is not a clean translation · rotation · scale.
 */
public final class PoseBlend {
	private PoseBlend() {
	}

	/**
	 * {@code weight} 0 gives {@code from}, 1 gives {@code to}; the model turns and resizes about {@code pivot}
	 * (in model space, usually its centre), which travels in a straight line.
	 */
	public static Matrix4f blend(Matrix4fc from, Matrix4fc to, float weight, Vector3fc pivot) {
		Matrix4f fromPivot = new Matrix4f(from).translate(pivot);
		Matrix4f toPivot = new Matrix4f(to).translate(pivot);
		return blend(fromPivot, toPivot, weight).translate(new Vector3f(pivot).negate());
	}

	/** {@code weight} 0 gives {@code from}, 1 gives {@code to}, turning about the model-space origin. */
	public static Matrix4f blend(Matrix4fc from, Matrix4fc to, float weight) {
		Matrix4f along = interpolate(from, to, weight);
		Matrix4f residue = new Matrix4f(from).mul(interpolate(from, to, 0).invert());
		Matrix4f fadingResidue = lerp(residue, new Matrix4f(), weight);
		return fadingResidue.mul(along);
	}

	private static Matrix4f interpolate(Matrix4fc from, Matrix4fc to, float weight) {
		Vector3f translation = from.getTranslation(new Vector3f()).lerp(to.getTranslation(new Vector3f()), weight);
		Quaternionf rotation = rotationOf(from).slerp(rotationOf(to), weight);
		Vector3f scale = from.getScale(new Vector3f()).lerp(to.getScale(new Vector3f()), weight);
		return new Matrix4f().translationRotateScale(translation, rotation, scale);
	}

	/** The rotation part, with each axis' scale divided out first. */
	private static Quaternionf rotationOf(Matrix4fc matrix) {
		Vector3f scale = matrix.getScale(new Vector3f());
		Matrix3f axes = matrix.get3x3(new Matrix3f()).scale(1 / scale.x, 1 / scale.y, 1 / scale.z);
		return new Quaternionf().setFromNormalized(axes);
	}

	private static Matrix4f lerp(Matrix4fc a, Matrix4fc b, float weight) {
		return new Matrix4f(a).lerp(b, weight);
	}
}
