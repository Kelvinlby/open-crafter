package mod.kelvinlby.opencrafter.client.handview;

import mod.kelvinlby.opencrafter.client.handview.anim.EatReturn;
import net.minecraft.world.entity.HumanoidArm;

import java.util.EnumMap;
import java.util.Map;

/**
 * Remembers each arm's last eating pose and plays it back down once the use ends (render thread,
 * ticked from the client tick).
 */
final class EatRelease {
	/** A pose this old (in ticks) no longer counts as "just stopped eating". */
	private static final double STALE_TICKS = 1;

	private final Map<HumanoidArm, Arm> arms = new EnumMap<>(HumanoidArm.class);
	private long ticks;

	EatRelease() {
		for (HumanoidArm arm : HumanoidArm.values()) {
			arms.put(arm, new Arm());
		}
	}

	void tick() {
		ticks++;
	}

	/** {@code arm} is drawn eating this frame, with the pose at {@code level}. */
	void noteEating(HumanoidArm arm, float level, float partialTicks) {
		Arm state = arms.get(arm);
		state.eatingAt = now(partialTicks);
		state.level = level;
		state.bob = 0;
	}

	void noteBob(HumanoidArm arm, float bob) {
		arms.get(arm).bob = bob;
	}

	/** {@code arm} is drawn without eating: starts its way back if it was eating a moment ago. */
	void noteNotEating(HumanoidArm arm, float partialTicks) {
		Arm state = arms.get(arm);
		double now = now(partialTicks);
		if (now - state.eatingAt <= STALE_TICKS) {
			state.motion.release(now, state.level, state.bob);
		}
		state.eatingAt = Double.NEGATIVE_INFINITY;
	}

	/** How far in the pose of the way back still is. */
	float level(HumanoidArm arm, float partialTicks) {
		return (float) arms.get(arm).motion.level(now(partialTicks));
	}

	/** How far in the eating pose {@code arm} is this frame: the eat noted for it, else the way back. */
	float current(HumanoidArm arm, float partialTicks) {
		Arm state = arms.get(arm);
		return state.eatingAt == now(partialTicks) ? state.level : level(arm, partialTicks);
	}

	float bob(HumanoidArm arm, float partialTicks) {
		return (float) arms.get(arm).motion.bob(now(partialTicks));
	}

	private double now(float partialTicks) {
		return ticks + partialTicks;
	}

	private static final class Arm {
		final EatReturn motion = new EatReturn();
		double eatingAt = Double.NEGATIVE_INFINITY;
		float level;
		float bob;
	}
}
