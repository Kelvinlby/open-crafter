package mod.kelvinlby.opencrafter.mixin.inventoryview;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import mod.kelvinlby.opencrafter.client.inventoryview.InventoryViewHooks;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Inventory view: the backpack's player model flies with the backpack. */
@Mixin(InventoryScreen.class)
abstract class InventoryScreenFlightMixin {
	@WrapOperation(method = "extractBackground", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/screens/inventory/InventoryScreen;extractEntityInInventoryFollowsMouse(Lnet/minecraft/client/gui/GuiGraphicsExtractor;IIIIIFFFLnet/minecraft/world/entity/LivingEntity;)V"))
	private void openCrafter$moveEntityBox(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int size, float yOffset,
		float mouseX, float mouseY, LivingEntity entity, Operation<Void> original) {
		int offset = InventoryViewHooks.entityBoxOffset();
		original.call(graphics, x0, y0 + offset, x1, y1 + offset, size, yOffset, mouseX, mouseY + offset, entity);
	}
}
