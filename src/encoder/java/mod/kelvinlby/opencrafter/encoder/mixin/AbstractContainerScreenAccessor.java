package mod.kelvinlby.opencrafter.encoder.mixin;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Replay: where a container screen sits, to point at a clicked slot. */
@Mixin(AbstractContainerScreen.class)
public interface AbstractContainerScreenAccessor {
	@Accessor("leftPos")
	int openCrafterEncoder$leftPos();

	@Accessor("topPos")
	int openCrafterEncoder$topPos();
}
