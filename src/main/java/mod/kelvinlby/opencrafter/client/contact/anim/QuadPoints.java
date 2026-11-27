package mod.kelvinlby.opencrafter.client.contact.anim;

import org.joml.Vector3fc;

import java.util.Arrays;
import java.util.List;

/** The four corner positions of one model quad, block-local. */
public record QuadPoints(List<Vector3fc> corners) {
	public QuadPoints {
		corners = List.copyOf(corners);
	}

	/** Order-independent identity of the quad's position: the same element in two models gives the same signature. */
	public String signature() {
		long[] keys = corners.stream().mapToLong(PointSet::key).sorted().toArray();
		return Arrays.toString(keys);
	}
}
