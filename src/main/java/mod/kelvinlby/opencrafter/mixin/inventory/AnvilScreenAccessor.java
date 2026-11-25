package mod.kelvinlby.opencrafter.mixin.inventory;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The anvil's name field; setting its value fires the screen's own rename responder. */
@Mixin(AnvilScreen.class)
public interface AnvilScreenAccessor {
	@Accessor("name")
	EditBox opencrafter$nameBox();
}
