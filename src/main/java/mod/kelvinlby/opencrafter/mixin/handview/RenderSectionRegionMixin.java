package mod.kelvinlby.opencrafter.mixin.handview;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import mod.kelvinlby.opencrafter.client.handview.HandViewHooks;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Hand view: what vanilla's chunk mesher draws at a block the hand is still morphing into. */
@Mixin(RenderSectionRegion.class)
abstract class RenderSectionRegionMixin {
	@ModifyReturnValue(method = "getBlockState", at = @At("RETURN"))
	private BlockState openCrafter$meshedState(BlockState state, @Local(argsOnly = true) BlockPos pos) {
		return HandViewHooks.meshedState(pos.getX(), pos.getY(), pos.getZ(), state);
	}
}
