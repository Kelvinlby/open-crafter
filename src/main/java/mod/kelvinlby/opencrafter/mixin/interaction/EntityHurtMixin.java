package mod.kelvinlby.opencrafter.mixin.interaction;

import mod.kelvinlby.opencrafter.client.interaction.InteractionHooks;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.protocol.game.ClientboundDamageEventPacket;
import net.minecraft.network.protocol.game.ClientboundHurtAnimationPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Interactions: the server reporting an entity hurt confirms an attack. Handled as vanilla handles it. */
@Mixin(ClientPacketListener.class)
abstract class EntityHurtMixin {
	// TAIL is only reached on the client thread: the network thread leaves through ensureRunningOnSameThread.
	@Inject(method = "handleDamageEvent", at = @At("TAIL"))
	private void openCrafter$onDamage(ClientboundDamageEventPacket packet, CallbackInfo ci) {
		InteractionHooks.onEntityHurt(packet.entityId());
	}

	@Inject(method = "handleHurtAnimation", at = @At("TAIL"))
	private void openCrafter$onHurtAnimation(ClientboundHurtAnimationPacket packet, CallbackInfo ci) {
		InteractionHooks.onEntityHurt(packet.id());
	}
}
