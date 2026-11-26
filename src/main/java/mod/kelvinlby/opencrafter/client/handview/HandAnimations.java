package mod.kelvinlby.opencrafter.client.handview;

import mod.kelvinlby.opencrafter.client.handview.anim.ReachMotion;
import mod.kelvinlby.opencrafter.client.handview.anim.ShakeMotion;
import mod.kelvinlby.opencrafter.client.handview.anim.StubSwing;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** The custom swing one hand is playing, if any; at most one owns the hand at a time. */
final class HandAnimations {
	enum Owner {
		VANILLA,
		STUB,
		SHAKE,
		REACH
	}

	final StubSwing stub = new StubSwing();
	final ShakeMotion shake = new ShakeMotion();
	final ReachMotion reach = new ReachMotion();
	private Owner owner = Owner.VANILLA;
	private @Nullable Vec3 reachTarget;

	Owner owner() {
		return owner;
	}

	@Nullable Vec3 reachTarget() {
		return reachTarget;
	}

	void playStub(double now) {
		take(Owner.STUB);
		stub.engage(now);
	}

	void playShake(double now) {
		take(Owner.SHAKE);
		shake.engage(now);
	}

	void playReach(double now, Vec3 target, double durationTicks) {
		take(Owner.REACH);
		reachTarget = target;
		reach.start(now, durationTicks);
	}

	/** Hands the hand back to vanilla's swing at once. */
	void playVanilla() {
		take(Owner.VANILLA);
	}

	void update(double now, boolean attackHeld, boolean useHeld) {
		stub.update(now, attackHeld);
		shake.update(now, useHeld);
		reach.update(now);
		if (!ownerActive()) {
			owner = Owner.VANILLA;
		}
	}

	private boolean ownerActive() {
		return switch (owner) {
			case VANILLA -> true;
			case STUB -> stub.active();
			case SHAKE -> shake.active();
			case REACH -> reach.active();
		};
	}

	private void take(Owner next) {
		if (owner != next) {
			stub.reset();
			shake.reset();
			reach.reset();
		}
		owner = next;
	}
}
