package mod.kelvinlby.opencrafter.mixin.handview;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import mod.kelvinlby.opencrafter.client.handview.HandViewHooks;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Hand view: what Sodium's chunk mesher draws at a block the hand is still morphing into. Skipped
 * when Sodium is not installed (or has changed), leaving the block drawn at once.
 */
@Pseudo
@Mixin(targets = "net.caffeinemc.mods.sodium.client.world.LevelSlice")
abstract class SodiumLevelSliceMixin {
	@ModifyReturnValue(method = "getBlockState(III)Lnet/minecraft/world/level/block/state/BlockState;", at = @At("RETURN"), require = 0)
	private BlockState openCrafter$meshedState(BlockState state, @Local(argsOnly = true, ordinal = 0) int x, @Local(argsOnly = true, ordinal = 1) int y,
		@Local(argsOnly = true, ordinal = 2) int z) {
		return HandViewHooks.meshedState(x, y, z, state);
	}
}
