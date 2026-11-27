package mod.kelvinlby.opencrafter.mixin.contact;

import mod.kelvinlby.opencrafter.client.contact.ContactHooks;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Contact effects: a hit entity is drawn pushed (its render state only; the entity does not move). */
@Mixin(EntityRenderDispatcher.class)
abstract class EntityExtractContactMixin {
	@Inject(method = "extractEntity", at = @At("RETURN"))
	private <E extends Entity> void openCrafter$pushHitEntity(E entity, float partialTicks, CallbackInfoReturnable<EntityRenderState> cir) {
		ContactHooks.onEntityExtracted(cir.getReturnValue(), entity.getId());
	}
}
