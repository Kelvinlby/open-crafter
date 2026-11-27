package mod.kelvinlby.opencrafter.mixin.inventoryview;

import com.llamalad7.mixinextras.sugar.Local;
import mod.kelvinlby.opencrafter.client.inventoryview.InventoryViewHooks;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Inventory view: sees screens open and close, and draws a closed screen that is still flying out. */
@Mixin(Gui.class)
abstract class GuiFlightMixin {
	@Inject(method = "setScreen", at = @At("HEAD"))
	private void openCrafter$beforeSetScreen(Screen screen, CallbackInfo ci) {
		InventoryViewHooks.beforeScreenChange(((Gui) (Object) this).screen());
	}

	@Inject(method = "setScreen", at = @At("RETURN"))
	private void openCrafter$afterSetScreen(Screen screen, CallbackInfo ci) {
		InventoryViewHooks.afterScreenChange(((Gui) (Object) this).screen());
	}

	@Inject(method = "extractRenderState", at = @At(value = "INVOKE", shift = At.Shift.AFTER,
		target = "Lnet/minecraft/client/gui/Hud;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V"))
	private void openCrafter$drawClosingScreen(DeltaTracker deltaTracker, boolean a, boolean b, CallbackInfo ci, @Local GuiGraphicsExtractor graphics) {
		InventoryViewHooks.drawClosingScreen(graphics, deltaTracker);
	}
}
