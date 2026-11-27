package mod.kelvinlby.opencrafter.client.interaction.capture;

/** The index of the last frame the vision sensor captured, so actions can be placed between frames. */
public final class FrameClock {
	private volatile long lastCaptured = -1L;

	public void onCaptured(long frameIndex) {
		lastCaptured = frameIndex;
	}

	long lastCaptured() {
		return lastCaptured;
	}
}
