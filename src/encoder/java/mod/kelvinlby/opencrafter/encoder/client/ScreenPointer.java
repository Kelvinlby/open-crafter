package mod.kelvinlby.opencrafter.encoder.client;

import com.mojang.blaze3d.platform.Window;
import mod.kelvinlby.opencrafter.encoder.mixin.AbstractContainerScreenAccessor;
import mod.kelvinlby.opencrafter.encoder.mixin.MouseHandlerAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.Slot;

/**
 * Recordings do not contain the mouse, but every click names its slot: the pointer is moved onto
 * that slot before the click, so the hover highlight and a carried stack show where the player was.
 */
final class ScreenPointer {
	private static final int SLOT_CENTER = 8;

	void pointAt(Minecraft minecraft, int slotIndex) {
		if (!(minecraft.gui.screen() instanceof AbstractContainerScreen<?> screen)) {
			return;
		}
		if (slotIndex < 0 || slotIndex >= screen.getMenu().slots.size()) {
			return;
		}
		Slot slot = screen.getMenu().slots.get(slotIndex);
		AbstractContainerScreenAccessor origin = (AbstractContainerScreenAccessor) screen;
		moveTo(minecraft.getWindow(), (MouseHandlerAccessor) minecraft.mouseHandler,
			origin.openCrafterEncoder$leftPos() + slot.x + SLOT_CENTER, origin.openCrafterEncoder$topPos() + slot.y + SLOT_CENTER);
	}

	private static void moveTo(Window window, MouseHandlerAccessor mouse, double guiX, double guiY) {
		mouse.openCrafterEncoder$setXpos(guiX * window.getScreenWidth() / window.getGuiScaledWidth());
		mouse.openCrafterEncoder$setYpos(guiY * window.getScreenHeight() / window.getGuiScaledHeight());
	}
}
