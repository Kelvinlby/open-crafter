package mod.kelvinlby.opencrafter.client.handview;

import mod.kelvinlby.opencrafter.client.handview.anim.OutAndBack;
import mod.kelvinlby.opencrafter.client.handview.anim.ShakeMotion;
import mod.kelvinlby.opencrafter.client.handview.anim.StubSwing;
import mod.kelvinlby.opencrafter.client.handview.anim.Travel;
import org.jspecify.annotations.Nullable;

/** The custom swing one hand is playing, if any; at most one owns the hand at a time. */
final class HandAnimations {
	enum Owner {
		VANILLA,
		/** Left click held: a short jab that trembles. */
		STUB,
		/** Right click on an entity or block: a shake while held. */
		SHAKE,
		/** A 3D block item placed: it turns into the placed block. */
		MORPH,
		/** A flat item placed: a small swing out and back. */
		JAB
	}

	/** Where a morph is: on its way, at the block (shown for one more tick while the block is redrawn), done. */
	enum MorphPhase {
		TRAVEL,
		ARRIVED,
		DONE
	}

	/** What a tick's update changed for the morph. */
	enum MorphEvent {
		NONE,
		/** It reached the block: the block may show up. */
		ARRIVED,
		/** The hand let go of it: the next item comes up. */
		SETTLED
	}

	/** How far into the swing the jab goes. */
	static final float JAB_PROGRESS = 0.05F;

	final StubSwing stub = new StubSwing();
	final ShakeMotion shake = new ShakeMotion();
	final OutAndBack outAndBack = new OutAndBack();
	final Travel travel = new Travel();
	private Owner owner = Owner.VANILLA;
	private BlockMorph.@Nullable Target morph;
	private MorphPhase morphPhase = MorphPhase.DONE;

	Owner owner() {
		return owner;
	}

	/** The morph being drawn (on its way or at the block), or null. */
	BlockMorph.@Nullable Target drawnMorph() {
		return owner == Owner.MORPH && morphPhase != MorphPhase.DONE ? morph : null;
	}

	/** The morph still on its way, whose block stays held back. */
	BlockMorph.@Nullable Target travellingMorph() {
		return owner == Owner.MORPH && morphPhase == MorphPhase.TRAVEL ? morph : null;
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
		morphPhase = MorphPhase.TRAVEL;
		travel.start(now, durationTicks);
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
	MorphEvent update(double now, boolean attackHeld, boolean useHeld, boolean vanillaSwinging) {
		stub.update(now, attackHeld);
		shake.update(now, useHeld);
		outAndBack.update(now);
		MorphEvent event = advanceMorph(now);
		if (!ownerActive() && !vanillaSwinging) {
			owner = Owner.VANILLA;
		}
		return event;
	}

	private MorphEvent advanceMorph(double now) {
		if (owner != Owner.MORPH) {
			return MorphEvent.NONE;
		}
		if (morphPhase == MorphPhase.ARRIVED) {
			morphPhase = MorphPhase.DONE;
			return MorphEvent.SETTLED;
		}
		if (morphPhase == MorphPhase.TRAVEL && travel.arrived(now)) {
			morphPhase = MorphPhase.ARRIVED;
			return MorphEvent.ARRIVED;
		}
		return MorphEvent.NONE;
	}

	// A finished animation keeps the hand (at rest) until the vanilla swing it replaced has ended too,
	// so it never hands over to that swing midway
	private boolean ownerActive() {
		return switch (owner) {
			case VANILLA -> true;
			case STUB -> stub.active();
			case SHAKE -> shake.active();
			case MORPH -> morphPhase != MorphPhase.DONE;
			case JAB -> outAndBack.active();
		};
	}

	private void take(Owner next) {
		if (owner != next) {
			stub.reset();
			shake.reset();
			outAndBack.reset();
			travel.reset();
			morphPhase = MorphPhase.DONE;
		}
		owner = next;
	}
}
