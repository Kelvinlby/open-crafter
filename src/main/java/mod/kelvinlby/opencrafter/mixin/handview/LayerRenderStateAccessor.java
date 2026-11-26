package mod.kelvinlby.opencrafter.mixin.handview;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.resources.model.geometry.ItemQuads;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Hand view: one layer of a held item: its quads and the display transform it is drawn with. */
@Mixin(ItemStackRenderState.LayerRenderState.class)
public interface LayerRenderStateAccessor {
	@Accessor("quads")
	ItemQuads openCrafter$quads();

	@Accessor("specialRenderer")
	SpecialModelRenderer<Object> openCrafter$specialRenderer();

	@Invoker("applyTransform")
	void openCrafter$applyTransform(PoseStack.Pose pose);
}
