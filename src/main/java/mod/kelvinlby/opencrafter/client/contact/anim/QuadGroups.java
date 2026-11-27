package mod.kelvinlby.opencrafter.client.contact.anim;

import org.joml.Vector3fc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Splits a model's quads into what stayed put and the rigid parts that moved. */
public final class QuadGroups {
	private QuadGroups() {
	}

	/** Indices of the quads of {@code quads} that have no counterpart at the same place in {@code other}. */
	public static List<Integer> moved(List<QuadPoints> quads, List<QuadPoints> other) {
		Set<String> still = new HashSet<>();
		other.forEach(quad -> still.add(quad.signature()));
		List<Integer> moved = new ArrayList<>();
		for (int i = 0; i < quads.size(); i++) {
			if (!still.contains(quads.get(i).signature())) {
				moved.add(i);
			}
		}
		return moved;
	}

	/** Groups of quad indices connected by shared corners: each group is one rigid part (a door, a gate wing). */
	public static List<List<Integer>> parts(List<QuadPoints> quads, List<Integer> indices) {
		int[] parent = new int[quads.size()];
		indices.forEach(i -> parent[i] = i);
		Map<Long, Integer> cornerOwner = new HashMap<>();
		for (int i : indices) {
			for (Vector3fc corner : quads.get(i).corners()) {
				Integer earlier = cornerOwner.putIfAbsent(PointSet.key(corner), i);
				if (earlier != null) {
					union(parent, earlier, i);
				}
			}
		}
		Map<Integer, List<Integer>> groups = new LinkedHashMap<>();
		indices.forEach(i -> groups.computeIfAbsent(find(parent, i), ignored -> new ArrayList<>()).add(i));
		return new ArrayList<>(groups.values());
	}

	public static List<QuadPoints> select(List<QuadPoints> quads, List<Integer> indices) {
		return indices.stream().map(quads::get).toList();
	}

	private static int find(int[] parent, int i) {
		while (parent[i] != i) {
			parent[i] = parent[parent[i]];
			i = parent[i];
		}
		return i;
	}

	private static void union(int[] parent, int a, int b) {
		parent[find(parent, a)] = find(parent, b);
	}
}
