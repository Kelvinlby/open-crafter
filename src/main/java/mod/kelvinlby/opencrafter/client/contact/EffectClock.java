package mod.kelvinlby.opencrafter.client.contact;

import net.minecraft.client.Minecraft;

/** Time for contact effects: client ticks, plus the partial tick of the frame being rendered. */
public final class EffectClock {
	private long ticks;

	void tick() {
		ticks++;
	}

	/** The time at the end of the latest tick. */
	public double tickTime() {
		return ticks;
	}

	/** The time of the frame being rendered (ticks plus the partial tick; frozen while paused). */
	public double frameTime() {
		return ticks + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
	}
}
