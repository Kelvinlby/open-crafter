package mod.kelvinlby.opencrafter.client.inventory;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Quick-craft ("drag") rules of {@code AbstractContainerScreen.shouldAddSlotToQuickCraft}. */
final class DragPlanner {
	/** Quick-craft type: 0 = split evenly (left), 1 = one each (right), 2 = full stacks (middle, creative). */
	static final int SPLIT = 0;
	static final int ONE_EACH = 1;
	static final int CLONE = 2;

	private DragPlanner() {
	}

	static int parseType(String button) {
		return switch (button.toLowerCase(Locale.ROOT)) {
			case "left" -> SPLIT;
			case "right" -> ONE_EACH;
			case "middle" -> CLONE;
			default -> throw new IllegalArgumentException("Drag button must be left, right or middle, got '" + button + "'");
		};
	}

	/** The requested slots the vanilla screen would add to the drag, in order, without duplicates. */
	static List<Integer> acceptedSlots(AbstractContainerMenu menu, List<Integer> requested, int type) {
		ItemStack carried = menu.getCarried();
		List<Integer> accepted = new ArrayList<>();
		for (int index : requested) {
			Slot slot = Slots.require(menu, index);
			if (!accepted.contains(index) && accepts(menu, slot, carried, type, accepted.size())) {
				accepted.add(index);
			}
		}
		return accepted;
	}

	private static boolean accepts(AbstractContainerMenu menu, Slot slot, ItemStack carried, int type, int alreadyAdded) {
		return (carried.getCount() > alreadyAdded || type == CLONE)
			&& AbstractContainerMenu.canItemQuickReplace(slot, carried, true)
			&& slot.mayPlace(carried)
			&& menu.canDragTo(slot);
	}
}
