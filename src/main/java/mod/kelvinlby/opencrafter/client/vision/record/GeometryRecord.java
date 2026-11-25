package mod.kelvinlby.opencrafter.client.vision.record;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;

import java.util.Arrays;

/**
 * View-space vertex positions one submitted object emitted in one frame, split into segments
 * of a single primitive topology, in emission order. Two records of the same object describe
 * the same surface points vertex-for-vertex when their shapes match.
 */
public final class GeometryRecord {
	private final RecordPhase phase;
	private float[] positions = new float[3 * 32];
	private int vertexCount;
	private PrimitiveTopology[] topologies = new PrimitiveTopology[4];
	private int[] segmentStarts = new int[4];
	private int segmentCount;

	public GeometryRecord(RecordPhase phase) {
		this.phase = phase;
	}

	public RecordPhase phase() {
		return phase;
	}

	/** Starts a segment unless the previous one has the same independent-primitive topology. */
	public void beginSegment(PrimitiveTopology topology) {
		if (segmentCount > 0 && topologies[segmentCount - 1] == topology && !topology.connectedPrimitives) {
			return;
		}
		if (segmentCount == topologies.length) {
			topologies = Arrays.copyOf(topologies, segmentCount * 2);
			segmentStarts = Arrays.copyOf(segmentStarts, segmentCount * 2);
		}
		topologies[segmentCount] = topology;
		segmentStarts[segmentCount] = vertexCount;
		segmentCount++;
	}

	public void addVertex(float x, float y, float z) {
		if (segmentCount == 0) {
			return;
		}
		int offset = vertexCount * 3;
		if (offset + 3 > positions.length) {
			positions = Arrays.copyOf(positions, positions.length * 2);
		}
		positions[offset] = x;
		positions[offset + 1] = y;
		positions[offset + 2] = z;
		vertexCount++;
	}

	public int vertexCount() {
		return vertexCount;
	}

	public int segmentCount() {
		return segmentCount;
	}

	public PrimitiveTopology topology(int segment) {
		return topologies[segment];
	}

	public int segmentStart(int segment) {
		return segmentStarts[segment];
	}

	public int segmentEnd(int segment) {
		return segment + 1 < segmentCount ? segmentStarts[segment + 1] : vertexCount;
	}

	public float coordinate(int vertex, int axis) {
		return positions[vertex * 3 + axis];
	}

	/** True when both records consist of the same segments (topology and vertex count). */
	public boolean sameShape(GeometryRecord other) {
		if (vertexCount != other.vertexCount || segmentCount != other.segmentCount) {
			return false;
		}
		for (int i = 0; i < segmentCount; i++) {
			if (topologies[i] != other.topologies[i] || segmentStarts[i] != other.segmentStarts[i]) {
				return false;
			}
		}
		return true;
	}
}
