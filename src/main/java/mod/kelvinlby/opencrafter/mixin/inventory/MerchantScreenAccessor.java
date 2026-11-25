package mod.kelvinlby.opencrafter.mixin.inventory;

import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** What a trade-offer button press does: set {@code shopItem}, then {@code postButtonClick()}. */
@Mixin(MerchantScreen.class)
public interface MerchantScreenAccessor {
	@Accessor("shopItem")
	void opencrafter$setShopItem(int shopItem);

	@Invoker("postButtonClick")
	void opencrafter$postButtonClick();
}
