package mod.kelvinlby.opencrafter.client.status;

/**
 * The air bubble row exactly as {@code Hud.extractAirBubbles} computes it (10 bubbles).
 *
 * @param full    bubbles drawn as full
 * @param popping true when the bubble right after the full ones is drawn popping
 * @param empty   bubbles drawn as empty (from the right end of the row)
 */
record AirBubbles(int full, boolean popping, int empty) {
	static final int ROW = 10;

	static AirBubbles of(int airSupply, int maxAirSupply, boolean eyesInWater) {
		if (maxAirSupply <= 0) {
			return new AirBubbles(0, false, 0);
		}
		int current = Math.clamp(airSupply, 0, maxAirSupply);
		int full = bubble(current, maxAirSupply, -2);
		int poppingPosition = bubble(current, maxAirSupply, 0);
		int emptyDelay = current != 0 && eyesInWater ? 1 : 0;
		int empty = ROW - bubble(current, maxAirSupply, emptyDelay);
		return new AirBubbles(full, eyesInWater && full != poppingPosition, empty);
	}

	/** True when vanilla draws the row at all (ignoring the hidden-HUD option and game mode). */
	static boolean drawn(int airSupply, int maxAirSupply, boolean eyesInWater) {
		return eyesInWater || Math.clamp(airSupply, 0, maxAirSupply) < maxAirSupply;
	}

	/** {@code Mth.ceil((current + offset) * 10f / max)}. */
	private static int bubble(int current, int max, int tickOffset) {
		return (int) Math.ceil((float) ((current + tickOffset) * ROW) / max);
	}
}
