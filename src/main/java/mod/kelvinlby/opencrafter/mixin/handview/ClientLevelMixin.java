package mod.kelvinlby.opencrafter.mixin.handview;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import mod.kelvinlby.opencrafter.client.handview.HandViewHooks;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hand view: the server's word on a block the hand is still morphing into is kept back and applied
 * when the morph arrives, so the block shows up then (and as the server has it).
 */
@Mixin(ClientLevel.class)
abstract class ClientLevelMixin {
	@WrapOperation(method = "setServerVerifiedBlockState", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/world/level/Level;setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z"))
	private boolean openCrafter$serverBlock(ClientLevel level, BlockPos pos, BlockState state, int flags, int limit, Operation<Boolean> original) {
		return HandViewHooks.holdsBackBlock(pos, state) || original.call(level, pos, state, flags, limit);
	}

	@Inject(method = "syncBlockState", at = @At("HEAD"), cancellable = true)
	private void openCrafter$syncBlock(BlockPos pos, BlockState state, Vec3 playerPos, CallbackInfo ci) {
		if (HandViewHooks.holdsBackBlock(pos, state)) {
			ci.cancel();
		}
	}
}
