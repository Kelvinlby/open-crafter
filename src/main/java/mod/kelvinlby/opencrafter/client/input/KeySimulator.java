package mod.kelvinlby.opencrafter.client.input;

import mod.kelvinlby.opencrafter.client.input.hold.HoldTable;
import mod.kelvinlby.opencrafter.client.input.hold.KeyStateMachine;
import mod.kelvinlby.opencrafter.client.input.keys.KeyMappingPort;
import mod.kelvinlby.opencrafter.client.input.keys.KeyMappings;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Util;

import java.util.Collection;
import java.util.List;

/**
 * Simulated key-mapping holds: a {@link HoldTable} of leases feeding a {@link KeyStateMachine}
 * that drives the real mappings. Main thread only; re-evaluated on every op, frame and tick.
 */
final class KeySimulator {
	/** A press is held for the tick after it was made, then released at the start of the next one. */
	private static final int PRESS_TICKS = 2;

	private final HoldTable holds = new HoldTable();
	private final KeyStateMachine machine = new KeyStateMachine(new KeyMappingPort());
	private long tick;
	private boolean updating;

	/** Declarative state: exactly these mappings are streamed-held for {@code leaseMillis}. */
	void stream(Collection<?> ids, long leaseMillis) {
		List<String> names = ids.stream().map(id -> canonical(String.valueOf(id))).toList();
		holds.stream(names, now() + leaseMillis);
		update();
	}

	void down(String id, long durationMillis) {
		holds.holdUntil(canonical(id), now() + durationMillis);
		update();
	}

	void up(String id) {
		holds.release(canonical(id));
		update();
	}

	/** Down now, up at the start of the second following tick: exactly one click, one tick down. */
	void press(String id) {
		String name = canonical(id);
		if (machine.isHeld(name)) {
			machine.repress(name, gameTakesInput());
		}
		holds.pulse(name, tick + PRESS_TICKS);
		update();
	}

	void releaseAll() {
		holds.releaseAll();
		machine.releaseAll();
	}

	void onTickStart() {
		tick++;
		update();
	}

	boolean isHeld(KeyMapping mapping) {
		return machine.isHeld(mapping.getName());
	}

	boolean isApplied(KeyMapping mapping) {
		return machine.isApplied(mapping.getName());
	}

	/** Re-evaluates leases and applies the result; reentrant calls (from vanilla hooks) are skipped. */
	void update() {
		if (updating) {
			return;
		}
		updating = true;
		try {
			machine.reconcile(holds.held(now(), tick), gameTakesInput());
		} finally {
			updating = false;
		}
	}

	private static String canonical(String id) {
		return KeyMappings.require(id).getName();
	}

	/** Same condition under which vanilla routes keys to mappings and runs {@code handleKeybinds}. */
	private static boolean gameTakesInput() {
		Minecraft minecraft = Minecraft.getInstance();
		return minecraft.gui.screen() == null && minecraft.gui.overlay() == null;
	}

	private static long now() {
		return Util.getMillis();
	}
}
