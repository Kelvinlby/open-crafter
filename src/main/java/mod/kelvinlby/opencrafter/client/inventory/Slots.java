package mod.kelvinlby.opencrafter.client.inventory;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;

import java.util.ArrayList;
import java.util.List;

/** Slot index validation, mirroring which slots the vanilla screen lets the mouse reach. */
final class Slots {
	/** Vanilla's slot id for "clicked outside the GUI" (and for quick-craft start/end). */
	static final int OUTSIDE = -999;

	private Slots() {
	}

	static Slot require(AbstractContainerMenu menu, int index) {
		if (index < 0 || index >= menu.slots.size()) {
			throw new IllegalArgumentException("Slot " + index + " out of range [0, " + menu.slots.size() + ")");
		}
		Slot slot = menu.getSlot(index);
		if (!slot.isActive()) {
			throw new IllegalArgumentException("Slot " + index + " is not active on this screen");
		}
		return slot;
	}

	static Slot requireWithItem(AbstractContainerMenu menu, int index) {
		Slot slot = require(menu, index);
		if (!slot.hasItem()) {
			throw new IllegalArgumentException("Slot " + index + " is empty");
		}
		return slot;
	}

	static List<Integer> indices(List<?> values) {
		List<Integer> result = new ArrayList<>(values.size());
		for (Object value : values) {
			if (!(value instanceof Number number)) {
				throw new IllegalArgumentException("Slot list must contain integers, got " + value);
			}
			result.add(number.intValue());
		}
		return result;
	}
}
