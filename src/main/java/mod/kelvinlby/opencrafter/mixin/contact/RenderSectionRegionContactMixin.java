package mod.kelvinlby.opencrafter.mixin.contact;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import mod.kelvinlby.opencrafter.client.contact.ContactHooks;
import net.minecraft.client.renderer.chunk.RenderSectionRegion;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Contact effects: vanilla's chunk mesher leaves out a block while its temporary model swings in its place. */
@Mixin(RenderSectionRegion.class)
abstract class RenderSectionRegionContactMixin {
	@ModifyReturnValue(method = "getBlockState", at = @At("RETURN"))
	private BlockState openCrafter$hideSwingingBlock(BlockState state, @Local(argsOnly = true) BlockPos pos) {
		return ContactHooks.meshedState(pos.getX(), pos.getY(), pos.getZ(), state);
	}
}
