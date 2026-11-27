package mod.kelvinlby.opencrafter.client.contact.anim;

import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * How one part of a block's new model is moved back towards where that part was in the old
 * model. {@link #at} with {@code remaining = 1} is the old pose, {@code 0} the new one.
 * Block-local coordinates (one block = 1).
 */
public sealed interface PartMotion {
	Matrix4f at(double remaining);

	/** A part that is the same in both models. */
	PartMotion STILL = new Still();

	record Still() implements PartMotion {
		@Override
		public Matrix4f at(double remaining) {
			return new Matrix4f();
		}
	}

	/** A part turning about a hinge: {@code angle} radians (right hand) about {@code axis} through {@code pivot}. */
	record Swing(Vector3fc axis, Vector3fc pivot, float angle) implements PartMotion {
		@Override
		public Matrix4f at(double remaining) {
			return new Matrix4f()
				.translate(pivot)
				.rotate((float) (angle * remaining), axis)
				.translate(new Vector3f(pivot).negate());
		}

		/** The same hinge turning the other way. */
		public Swing reversed() {
			return new Swing(axis, pivot, -angle);
		}
	}

	/** A part that is moved and stretched along the axes (a button pressed in): blended linearly. */
	record Stretch(Matrix4fc full) implements PartMotion {
		@Override
		public Matrix4f at(double remaining) {
			return new Matrix4f().lerp(full, (float) remaining);
		}
	}
}
