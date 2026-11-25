package mod.kelvinlby.opencrafter.client.inventory;

import mod.kelvinlby.opencrafter.mixin.inventory.ContainerScreenInvoker;
import mod.kelvinlby.opencrafter.mixin.inventory.CreativeScreenAccessor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;

/**
 * Sends a click through the open screen's own {@code slotClicked}, exactly as its mouse/key
 * handlers do, so screen overrides (creative, crafter), bundle hooks and client prediction apply.
 */
final class SlotClicker {
	private SlotClicker() {
	}

	static void click(AbstractContainerScreen<?> screen, int slotId, int button, ContainerInput input) {
		Slot slot = slotId == Slots.OUTSIDE ? null : screen.getMenu().getSlot(slotId);
		markOutsideClick(screen, slot);
		((ContainerScreenInvoker) screen).opencrafter$slotClicked(slot, slotId, button, input);
	}

	/** The creative screen only drops the carried stack if its mouse handler saw an outside click. */
	private static void markOutsideClick(AbstractContainerScreen<?> screen, @Nullable Slot slot) {
		if (screen instanceof CreativeModeInventoryScreen) {
			((CreativeScreenAccessor) screen).opencrafter$setHasClickedOutside(slot == null);
		}
	}
}
