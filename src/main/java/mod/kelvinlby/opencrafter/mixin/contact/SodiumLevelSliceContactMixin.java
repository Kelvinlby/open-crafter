package mod.kelvinlby.opencrafter.mixin.contact;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import mod.kelvinlby.opencrafter.client.contact.ContactHooks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Contact effects: Sodium's chunk mesher leaves out a block while its temporary model swings in
 * its place. Skipped when Sodium is not installed (or has changed).
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.world.LevelSlice")
abstract class SodiumLevelSliceContactMixin {
	@ModifyReturnValue(method = "getBlockState(III)Lnet/minecraft/world/level/block/state/BlockState;", at = @At("RETURN"), require = 0)
	private BlockState openCrafter$hideSwingingBlock(BlockState state, @Local(argsOnly = true, ordinal = 0) int x, @Local(argsOnly = true, ordinal = 1) int y,
		@Local(argsOnly = true, ordinal = 2) int z) {
		return ContactHooks.meshedState(x, y, z, state);
	}
}
