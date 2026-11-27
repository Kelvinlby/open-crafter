package mod.kelvinlby.opencrafter.encoder.mixin;

import mod.kelvinlby.opencrafter.encoder.client.ReplayHooks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

/** Replay: the local player's own movement is steered onto the recorded path. */
@Mixin(Entity.class)
abstract class EntityReplayMixin {
	@ModifyVariable(method = "move", at = @At("HEAD"), argsOnly = true)
	private Vec3 openCrafterEncoder$steer(Vec3 delta, MoverType type) {
		return ReplayHooks.steerMove((Entity) (Object) this, type, delta);
	}
}
