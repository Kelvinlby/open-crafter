package mod.kelvinlby.opencrafter.mixin.inventory;

import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Creative screen state that the vanilla mouse/keyboard handlers maintain. */
@Mixin(CreativeModeInventoryScreen.class)
public interface CreativeScreenAccessor {
	@Accessor("hasClickedOutside")
	void opencrafter$setHasClickedOutside(boolean clickedOutside);

	@Accessor("scrollOffs")
	void opencrafter$setScrollOffs(float scrollOffs);

	@Accessor("searchBox")
	EditBox opencrafter$searchBox();

	@Invoker("refreshSearchResults")
	void opencrafter$refreshSearchResults();
}
