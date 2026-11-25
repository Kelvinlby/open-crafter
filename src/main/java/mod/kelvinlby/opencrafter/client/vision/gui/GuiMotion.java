package mod.kelvinlby.opencrafter.client.vision.gui;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Which GUI elements moved, vanished or appeared between two frames. Elements are matched by
 * {@link GuiElement#key()}; among equal keys, elements that stayed put match first, then the
 * rest pair up nearest-first (picking one of three identical items matches the two that stayed
 * with themselves and the third with the one now on the cursor).
 */
public record GuiMotion(List<Pair> moved, List<GuiElement> vanished, List<GuiElement> appeared) {
	/** Beyond this many unmatched elements of one key, pairing is skipped (they count as replaced). */
	private static final int MAX_PAIRING = 64;

	public record Pair(GuiElement prev, GuiElement cur) {
	}

	public boolean isEmpty() {
		return moved.isEmpty() && vanished.isEmpty() && appeared.isEmpty();
	}

	public static GuiMotion between(GuiFrame prev, GuiFrame cur) {
		GuiMotion motion = new GuiMotion(new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
		if (prev.width() != cur.width() || prev.height() != cur.height()) {
			return motion;
		}
		Long2ObjectOpenHashMap<List<GuiElement>> current = groupByKey(cur.elements());
		for (List<GuiElement> prevGroup : groupByKey(prev.elements()).values()) {
			List<GuiElement> curGroup = current.remove(prevGroup.getFirst().key());
			motion.match(new ArrayList<>(prevGroup), curGroup == null ? new ArrayList<>() : new ArrayList<>(curGroup));
		}
		current.values().forEach(motion.appeared::addAll);
		return motion;
	}

	private void match(List<GuiElement> prev, List<GuiElement> cur) {
		removeStationary(prev, cur);
		if (prev.size() <= MAX_PAIRING && cur.size() <= MAX_PAIRING) {
			pairNearest(prev, cur);
		}
		vanished.addAll(prev);
		appeared.addAll(cur);
	}

	/** Elements found at exactly the same place in both frames did not move. Hashing keeps this linear. */
	private static void removeStationary(List<GuiElement> prev, List<GuiElement> cur) {
		Long2ObjectOpenHashMap<List<GuiElement>> byPlace = new Long2ObjectOpenHashMap<>();
		for (GuiElement element : cur) {
			byPlace.computeIfAbsent(placeKey(element), (long k) -> new ArrayList<>()).add(element);
		}
		prev.removeIf(element -> {
			List<GuiElement> candidates = byPlace.get(placeKey(element));
			if (candidates == null) {
				return false;
			}
			for (GuiElement candidate : candidates) {
				if (element.samePlaceAs(candidate)) {
					candidates.remove(candidate);
					cur.remove(candidate);
					return true;
				}
			}
			return false;
		});
	}

	private static long placeKey(GuiElement element) {
		return Math.round(element.positions.getFloat(0) * 10.0F) * 1_000_003L + Math.round(element.positions.getFloat(1) * 10.0F);
	}

	private void pairNearest(List<GuiElement> prev, List<GuiElement> cur) {
		List<Pair> candidates = new ArrayList<>(prev.size() * cur.size());
		for (GuiElement p : prev) {
			for (GuiElement c : cur) {
				candidates.add(new Pair(p, c));
			}
		}
		candidates.sort(Comparator.comparingDouble(GuiMotion::distanceSquared));
		for (Pair candidate : candidates) {
			if (prev.contains(candidate.prev()) && cur.contains(candidate.cur())) {
				moved.add(candidate);
				prev.remove(candidate.prev());
				cur.remove(candidate.cur());
			}
		}
	}

	private static double distanceSquared(Pair pair) {
		double dx = pair.prev().centroidX() - pair.cur().centroidX();
		double dy = pair.prev().centroidY() - pair.cur().centroidY();
		return dx * dx + dy * dy;
	}

	private static Long2ObjectOpenHashMap<List<GuiElement>> groupByKey(List<GuiElement> elements) {
		Long2ObjectOpenHashMap<List<GuiElement>> groups = new Long2ObjectOpenHashMap<>();
		for (GuiElement element : elements) {
			if (element.vertexCount() > 0) {
				groups.computeIfAbsent(element.key(), (long k) -> new ArrayList<>()).add(element);
			}
		}
		return groups;
	}
}
