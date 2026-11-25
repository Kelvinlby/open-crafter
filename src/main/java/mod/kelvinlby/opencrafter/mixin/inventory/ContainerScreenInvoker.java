package mod.kelvinlby.opencrafter.mixin.inventory;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.world.inventory.ContainerInput;
import net.minecraft.world.inventory.Slot;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Exposes the screen's own slot-click entry point (virtual: creative/crafter overrides apply). */
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenInvoker {
	@Invoker("slotClicked")
	void opencrafter$slotClicked(@Nullable Slot slot, int slotId, int buttonNum, ContainerInput containerInput);
}
