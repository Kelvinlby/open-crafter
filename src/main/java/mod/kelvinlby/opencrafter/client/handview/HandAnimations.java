package mod.kelvinlby.opencrafter.client.handview;

import mod.kelvinlby.opencrafter.client.handview.anim.OutAndBack;
import mod.kelvinlby.opencrafter.client.handview.anim.ShakeMotion;
import mod.kelvinlby.opencrafter.client.handview.anim.StubSwing;
import org.jspecify.annotations.Nullable;

/** The custom swing one hand is playing, if any; at most one owns the hand at a time. */
final class HandAnimations {
	enum Owner {
		VANILLA,
		/** Left click held: a short jab that trembles. */
		STUB,
		/** Right click on an entity or block: a shake while held. */
		SHAKE,
		/** A 3D block item placed: it turns into the placed block and back. */
		MORPH,
		/** A flat item placed: a small swing out and back. */
		JAB
	}

	/** How far into the swing the jab goes. */
	static final float JAB_PROGRESS = 0.05F;

	final StubSwing stub = new StubSwing();
	final ShakeMotion shake = new ShakeMotion();
	final OutAndBack outAndBack = new OutAndBack();
	private Owner owner = Owner.VANILLA;
	private BlockMorph.@Nullable Target morph;

	Owner owner() {
		return owner;
	}

	/** The block the morph turns into while {@link Owner#MORPH} plays. */
	BlockMorph.@Nullable Target morph() {
		return morph;
	}

	void playStub(double now) {
		take(Owner.STUB);
		stub.engage(now);
	}

	void playShake(double now) {
		take(Owner.SHAKE);
		shake.engage(now);
	}

	void playMorph(double now, BlockMorph.Target target, double durationTicks) {
		take(Owner.MORPH);
		morph = target;
		outAndBack.start(now, durationTicks);
	}

	void playJab(double now, double durationTicks) {
		take(Owner.JAB);
		outAndBack.start(now, durationTicks);
	}

	/** Hands the hand back to vanilla's swing at once. */
	void playVanilla() {
		take(Owner.VANILLA);
	}

	/** Once per tick; {@code vanillaSwinging}: vanilla's own swing of this hand is still running underneath. */
	void update(double now, boolean attackHeld, boolean useHeld, boolean vanillaSwinging) {
		stub.update(now, attackHeld);
		shake.update(now, useHeld);
		outAndBack.update(now);
		if (!ownerActive() && !vanillaSwinging) {
			owner = Owner.VANILLA;
		}
	}

	// A finished animation keeps the hand (at rest) until the vanilla swing it replaced has ended too,
	// so it never hands over to that swing midway
	private boolean ownerActive() {
		return switch (owner) {
			case VANILLA -> true;
			case STUB -> stub.active();
			case SHAKE -> shake.active();
			case MORPH, JAB -> outAndBack.active();
		};
	}

	private void take(Owner next) {
		if (owner != next) {
			stub.reset();
			shake.reset();
			outAndBack.reset();
		}
		owner = next;
	}
}
