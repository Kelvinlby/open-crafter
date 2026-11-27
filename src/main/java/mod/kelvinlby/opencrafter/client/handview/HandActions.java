package mod.kelvinlby.opencrafter.client.handview;

import mod.kelvinlby.opencrafter.client.interaction.event.InteractionEvent;
import mod.kelvinlby.opencrafter.client.interaction.event.InteractionListener;
import mod.kelvinlby.opencrafter.client.interaction.event.InteractionTarget;
import mod.kelvinlby.opencrafter.client.interaction.event.Outcome;
import mod.kelvinlby.opencrafter.core.config.handview.HandViewConfig;
import mod.kelvinlby.opencrafter.core.config.handview.SwingConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.component.SwingAnimation;
import org.jspecify.annotations.Nullable;

import java.util.function.Supplier;

/**
 * Drives the hands from the local player's interactions, whoever sent them (keyboard, backend, other
 * mods): picks each action's animation, launches block flights from placements, aims the hand at
 * off-crosshair contacts, and tells the swings that belong to an action from purely cosmetic ones.
 *
 * <p>Attacks, stabs and breaks always swing. A right click (entity, block, item) sends its packet
 * even when it does nothing, so it animates once confirmed: by its sender's swing in the same tick
 * (vanilla swings only on success) or by an outcome with an effect.
 */
final class HandActions implements InteractionListener {
	/** What a right click was for. */
	private enum RightClick {
		/** Used an entity: shake while held. */
		ENTITY,
		/** Used a block without placing one: shake while held. */
		BLOCK,
		/** Used the item itself (throwing, ...): vanilla swing. */
		ITEM
	}

	/** An outcome of starting to use an item (eating, drawing a bow) is that use's own animation, not a swing. */
	private static final String ITEM_IN_USE = "item_in_use";

	private final Supplier<@Nullable HandViewConfig> config;
	private final SwingAnimator swings;
	private final BlockFlights flights;
	private final ContactAim aims;
	private final BreakRun breaks = new BreakRun();
	/** Per shown hand: the tick its last action was sent on, and the right click waiting to be confirmed. */
	private final long[] actionTicks = {Long.MIN_VALUE, Long.MIN_VALUE};
	private final @Nullable InteractionEvent[] pendingRight = new InteractionEvent[2];
	private final long[] pendingTicks = {Long.MIN_VALUE, Long.MIN_VALUE};
	private long ticks;

	HandActions(Supplier<@Nullable HandViewConfig> config, SwingAnimator swings, BlockFlights flights, ContactAim aims) {
		this.config = config;
		this.swings = swings;
		this.flights = flights;
		this.aims = aims;
	}

	/** The hand a swing of {@code hand} shows in. */
	static InteractionHand shown(SwingConfig config, InteractionHand hand) {
		return switch (config.swingHand) {
			case VANILLA -> hand;
			case MAIN_HAND -> InteractionHand.MAIN_HAND;
			case OFF_HAND -> InteractionHand.OFF_HAND;
		};
	}

	/** Client ticks as the hands' actions count them (add the partial tick for the time of a frame). */
	long ticks() {
		return ticks;
	}

	/** Start of every client tick: ends gone breaks, ticks the swings, keeps mining swings and aims going. */
	void tick(Minecraft minecraft) {
		ticks++;
		HandViewConfig active = config.get();
		breaks.tick(minecraft.level, ticks);
		swings.tick(minecraft, active == null ? null : active.swing, breaks.running());
		if (active == null) {
			breaks.reset();
			aims.reset();
			return;
		}
		continueMining(active.swing, minecraft.player);
		for (InteractionHand hand : InteractionHand.values()) {
			aims.update(hand, ticks, swings.playing(hand, minecraft.player) || (breaks.running() && breaks.hand() == hand));
		}
	}

	@Override
	public void onInteraction(InteractionEvent event) {
		HandViewConfig active = config.get();
		LocalPlayer player = Minecraft.getInstance().player;
		if (active == null || player == null) {
			return;
		}
		InteractionHand hand = shown(active.swing, actingHand(event));
		switch (event.kind()) {
			case ATTACK_ENTITY, STAB -> playLeft(active.swing, player, event, hand);
			case BREAK_START -> startBreak(active.swing, player, event, hand);
			case BREAK_DONE, BREAK_ABORT -> breaks.stop(ticks);
			case INTERACT_ENTITY, USE_ON_BLOCK, USE_ITEM -> awaitConfirmation(event, hand);
			case RELEASE_USE -> {
			}
		}
	}

	@Override
	public void onOutcome(InteractionEvent event, Outcome outcome) {
		HandViewConfig active = config.get();
		LocalPlayer player = Minecraft.getInstance().player;
		InteractionHand hand = active == null ? InteractionHand.MAIN_HAND : shown(active.swing, actingHand(event));
		boolean pending = takePending(event, hand);
		if (active == null || player == null || !outcome.effect()) {
			return;
		}
		PlacedBlock placed = Placements.placed(event, outcome);
		if (placed != null && active.swing.rightClick == SwingConfig.RightClickAnimation.SMOOTH) {
			playPlaced(active.swing, player, event, placed, hand);
		} else if (pending && !ITEM_IN_USE.equals(outcome.reason())) {
			playRight(active.swing, player, event, hand, false);
		}
	}

	/**
	 * After the local player's {@code hand} (as shown) started a swing: a swing in the tick of an
	 * action (or of a running break) belongs to it and confirms a waiting right click; any other
	 * swing claims no action and plays neutral.
	 */
	void onSwing(LivingEntity entity, InteractionHand hand) {
		HandViewConfig active = config.get();
		LocalPlayer player = Minecraft.getInstance().player;
		if (active == null || player == null || entity != player) {
			return;
		}
		if (!claimed(hand)) {
			swings.playNeutral(hand, swingTicks(player, active.swing));
			return;
		}
		InteractionEvent pending = pendingRight[hand.ordinal()];
		if (pending != null && pendingTicks[hand.ordinal()] == ticks && takePending(pending, hand)) {
			playRight(active.swing, player, pending, hand, true);
		}
	}

	private void startBreak(SwingConfig swing, LocalPlayer player, InteractionEvent event, InteractionHand hand) {
		if (event.target() instanceof InteractionTarget.Block block) {
			breaks.start(block.pos(), hand);
		}
		playLeft(swing, player, event, hand);
	}

	/** A running break swings on every tick, as vanilla's held attack key does (vanilla's own pace keeps it from restarting early). */
	private void continueMining(SwingConfig swing, @Nullable LocalPlayer player) {
		if (player != null && breaks.running() && swing.leftClick == SwingConfig.LeftClickAnimation.VANILLA) {
			player.swing(InteractionHand.MAIN_HAND, player.getMainHandItem().getAttackAnimation(), false);
		}
	}

	private void playLeft(SwingConfig swing, LocalPlayer player, InteractionEvent event, InteractionHand hand) {
		drive(event, hand);
		if (swing.leftClick == SwingConfig.LeftClickAnimation.STUB) {
			swings.playStub(hand);
		} else {
			swings.playVanilla(hand);
			startVanillaSwing(player, actingHand(event), event.held().getAttackAnimation());
		}
	}

	/** {@code vanillaPlaying}: the sender's own swing already plays (it confirmed the click). */
	private void playRight(SwingConfig swing, LocalPlayer player, InteractionEvent event, InteractionHand hand, boolean vanillaPlaying) {
		drive(event, hand);
		RightClick kind = rightClickOf(event);
		if (swing.rightClick != SwingConfig.RightClickAnimation.SMOOTH || kind == RightClick.ITEM) {
			swings.playVanilla(hand);
			if (!vanillaPlaying) {
				startVanillaSwing(player, actingHand(event), event.held().getInteractAnimation());
			}
		} else {
			swings.playShake(hand);
		}
	}

	/** A block was placed: its item flies into it (the hand holds still), or a flat item jabs. */
	private void playPlaced(SwingConfig swing, LocalPlayer player, InteractionEvent event, PlacedBlock placed, InteractionHand hand) {
		drive(event, hand);
		boolean flies = player.level() instanceof ClientLevel level
			&& flights.launch(player, level, placed, swing.swingTicks);
		swings.playPlaced(hand, flies, swingTicks(player, swing));
	}

	private void awaitConfirmation(InteractionEvent event, InteractionHand hand) {
		actionTicks[hand.ordinal()] = ticks;
		pendingTicks[hand.ordinal()] = ticks;
		pendingRight[hand.ordinal()] = event;
	}

	/** True (and no longer waiting) when {@code event} was the right click waiting on {@code hand}. */
	private boolean takePending(InteractionEvent event, InteractionHand hand) {
		InteractionEvent pending = pendingRight[hand.ordinal()];
		if (pending == null || pending.id() != event.id()) {
			return false;
		}
		pendingRight[hand.ordinal()] = null;
		return true;
	}

	/** {@code event} now drives {@code hand}: its swings this tick belong to it, and the hand turns to its contact when off the crosshair. */
	private void drive(InteractionEvent event, InteractionHand hand) {
		actionTicks[hand.ordinal()] = ticks;
		if (event.contact() != null && !event.crosshairAligned()) {
			aims.engage(hand, event.contact(), ticks);
		} else {
			aims.update(hand, ticks, false);
		}
	}

	private boolean claimed(InteractionHand hand) {
		return actionTicks[hand.ordinal()] == ticks || breaks.claims(hand, ticks);
	}

	/** Plays vanilla's swing as the action's own (for senders that did not swing; one that did restarts the same swing). */
	private static void startVanillaSwing(LocalPlayer player, InteractionHand hand, SwingAnimation animation) {
		player.swing(hand, animation, false);
	}

	private static RightClick rightClickOf(InteractionEvent event) {
		return switch (event.target()) {
			case InteractionTarget.Entity ignored -> RightClick.ENTITY;
			case InteractionTarget.Block ignored -> RightClick.BLOCK;
			case InteractionTarget.None ignored -> RightClick.ITEM;
		};
	}

	private static InteractionHand actingHand(InteractionEvent event) {
		return event.hand() == null ? InteractionHand.MAIN_HAND : event.hand();
	}

	/** The length of the player's current swing (the configured one when none plays), as the jab and the neutral swing are timed by. */
	private static int swingTicks(LivingEntity player, SwingConfig config) {
		LivingEntity.SwingDescription swing = player.getCurrentSwing();
		return swing == null ? config.swingTicks : swing.durationTicks();
	}
}
