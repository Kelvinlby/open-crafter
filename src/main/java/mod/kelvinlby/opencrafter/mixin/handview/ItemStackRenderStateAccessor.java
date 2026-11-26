package mod.kelvinlby.opencrafter.mixin.handview;

import net.minecraft.client.renderer.item.ItemStackRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Hand view: the layers of a held item's render state, to morph a placed block's item. */
@Mixin(ItemStackRenderState.class)
public interface ItemStackRenderStateAccessor {
	@Accessor("layers")
	ItemStackRenderState.LayerRenderState[] openCrafter$layers();

	@Accessor("activeLayerCount")
	int openCrafter$activeLayerCount();
}
