package mod.kelvinlby.opencrafter.client.inventory;

import mod.kelvinlby.opencrafter.api.Args;
import mod.kelvinlby.opencrafter.api.ThreadAffinity;
import mod.kelvinlby.opencrafter.api.handler.HandlerRegistry;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;
import java.util.Map;

/** Slot clicks: the raw {@code ContainerInput} primitive plus the multi-click gestures of the vanilla screen. */
final class ClickOps {
	private ClickOps() {
	}

	static void register(HandlerRegistry handlers) {
		handlers.register("inventory.click", ThreadAffinity.MAIN, ClickOps::click);
		handlers.register("inventory.drag", ThreadAffinity.MAIN, ClickOps::drag);
		handlers.register("inventory.quick_move_all", ThreadAffinity.MAIN, ClickOps::quickMoveAll);
	}

	/** {@code slot}, {@code action} (default pickup), {@code button} (default 0). */
	private static Object click(Args args) {
		AbstractContainerScreen<?> screen = Screens.containerScreen();
		ClickAction action = ClickAction.parse(args.string("action", "pickup"));
		int slot = args.integer("slot");
		int button = args.integer("button", 0);
		action.checkButton(button);
		checkSlot(screen.getMenu(), slot, action);
		checkCarriedForSwap(screen.getMenu(), action);
		SlotClicker.click(screen, slot, button, action.input());
		return carriedState(screen.getMenu());
	}

	/** {@code slots} (list), {@code button}: left | right | middle. Start, one add per slot, end. */
	private static Object drag(Args args) {
		AbstractContainerScreen<?> screen = Screens.containerScreen();
		AbstractContainerMenu menu = screen.getMenu();
		int type = DragPlanner.parseType(args.string("button", "left"));
		checkDragAllowed(menu, type);
		List<Integer> accepted = DragPlanner.acceptedSlots(menu, Slots.indices(args.list("slots")), type);
		if (accepted.isEmpty()) {
			throw new IllegalArgumentException("None of the slots accepts the carried item");
		}
		sendDrag(screen, accepted, type);
		return Map.of("slots", accepted, "carried_empty", menu.getCarried().isEmpty());
	}

	/** {@code slot}: shift-click it, then shift-double-click (moves every matching stack of that container). */
	private static Object quickMoveAll(Args args) {
		AbstractContainerScreen<?> screen = Screens.containerScreen();
		AbstractContainerMenu menu = screen.getMenu();
		Slot origin = Slots.requireWithItem(menu, args.integer("slot"));
		if (!menu.getCarried().isEmpty()) {
			throw new IllegalStateException("quick_move_all needs an empty cursor");
		}
		ItemStack moved = origin.getItem().copy();
		SlotClicker.click(screen, origin.index, 0, ContainerInput.QUICK_MOVE);
		int clicks = 1 + (menu.canTakeItemForPickAll(ItemStack.EMPTY, origin) ? quickMoveMatching(screen, origin, moved) : 0);
		return Map.of("clicks", clicks);
	}

	/** The loop {@code AbstractContainerScreen.mouseReleased} runs on a shift-double-click. */
	private static int quickMoveMatching(AbstractContainerScreen<?> screen, Slot origin, ItemStack moved) {
		int clicks = 0;
		for (Slot target : List.copyOf(screen.getMenu().slots)) {
			if (target.mayPickup(Screens.player())
				&& target.hasItem()
				&& target.container == origin.container
				&& AbstractContainerMenu.canItemQuickReplace(target, moved, true)) {
				SlotClicker.click(screen, target.index, 0, ContainerInput.QUICK_MOVE);
				clicks++;
			}
		}
		return clicks;
	}

	private static void sendDrag(AbstractContainerScreen<?> screen, List<Integer> slots, int type) {
		SlotClicker.click(screen, Slots.OUTSIDE, AbstractContainerMenu.getQuickcraftMask(0, type), ContainerInput.QUICK_CRAFT);
		for (int slot : slots) {
			SlotClicker.click(screen, slot, AbstractContainerMenu.getQuickcraftMask(1, type), ContainerInput.QUICK_CRAFT);
		}
		SlotClicker.click(screen, Slots.OUTSIDE, AbstractContainerMenu.getQuickcraftMask(2, type), ContainerInput.QUICK_CRAFT);
	}

	private static void checkSlot(AbstractContainerMenu menu, int slot, ClickAction action) {
		if (slot != Slots.OUTSIDE) {
			Slots.require(menu, slot);
		} else if (!action.allowsOutside()) {
			throw new IllegalArgumentException("Slot -999 (outside) is not valid for " + action.wireName());
		}
	}

	/** The vanilla screen only sends hotbar/offhand swaps while the cursor is empty. */
	private static void checkCarriedForSwap(AbstractContainerMenu menu, ClickAction action) {
		if (action == ClickAction.SWAP && !menu.getCarried().isEmpty()) {
			throw new IllegalStateException("swap needs an empty cursor");
		}
	}

	private static void checkDragAllowed(AbstractContainerMenu menu, int type) {
		if (menu.getCarried().isEmpty()) {
			throw new IllegalStateException("drag needs an item on the cursor");
		}
		if (!AbstractContainerMenu.isValidQuickcraftType(type, Screens.player())) {
			throw new IllegalStateException("middle drag needs creative mode");
		}
	}

	private static Map<String, Object> carriedState(AbstractContainerMenu menu) {
		return Map.of("carried_empty", menu.getCarried().isEmpty());
	}
}
