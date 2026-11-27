package mod.kelvinlby.opencrafter.mixin.inventoryview;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import mod.kelvinlby.opencrafter.client.inventoryview.InventoryViewHooks;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Inventory view: draws a flying screen moved, with its dim backdrop kept in place. */
@Mixin(Screen.class)
abstract class ScreenFlightMixin {
	@WrapMethod(method = "extractRenderStateWithTooltipAndSubtitles")
	private void openCrafter$drawFlying(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks, Operation<Void> original) {
		InventoryViewHooks.drawScreen((Screen) (Object) this, graphics, mouseX, mouseY, partialTicks,
			(g, x, y, partial) -> original.call(g, x, y, partial));
	}

	@WrapOperation(method = "extractTransparentBackground", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fillGradient(IIIIII)V"))
	private void openCrafter$drawDim(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int top, int bottom, Operation<Void> original) {
		InventoryViewHooks.drawDim((Screen) (Object) this, graphics, x0, y0, x1, y1, top, bottom,
			(g, a, b, c, d, t, u) -> original.call(g, a, b, c, d, t, u));
	}
}
