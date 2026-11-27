package mod.kelvinlby.opencrafter.client.contact.render;

import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.Direction;
import net.minecraft.world.level.CardinalLighting;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

/**
 * The block renderer darkens each face by the direction it faces (top brightest, bottom darkest).
 * A turned quad must be shaded for the direction it faces now, or a door swinging round would
 * keep the brightness of where it ends up (and jump at its first frame).
 */
final class FaceShade {
	private static final float FLAT = 1.0e-6F;

	private FaceShade() {
	}

	/** The factor that turns the shade the renderer gave {@code quad} into the shade of its turned face. */
	static float correction(CardinalLighting lighting, BakedQuad quad, Matrix4fc transform) {
		Direction shadedAs = quad.materialInfo().shadeDirectionOverride() != null ? quad.materialInfo().shadeDirectionOverride() : quad.direction();
		float given = lighting.byFace(shadedAs);
		Vector3f normal = transform.transformDirection(new Vector3f(shadedAs.getUnitVec3f()));
		if (given < FLAT || normal.lengthSquared() < FLAT) {
			return 1.0F;
		}
		return shade(lighting, normal.normalize()) / given;
	}

	/** Shade of a face with this unit normal: the axis shades blended by the squared normal components. */
	static float shade(CardinalLighting lighting, Vector3f normal) {
		float x = normal.x * normal.x * (normal.x > 0 ? lighting.east() : lighting.west());
		float y = normal.y * normal.y * (normal.y > 0 ? lighting.up() : lighting.down());
		float z = normal.z * normal.z * (normal.z > 0 ? lighting.south() : lighting.north());
		return x + y + z;
	}
}
