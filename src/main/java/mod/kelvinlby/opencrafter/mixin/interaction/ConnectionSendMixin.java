package mod.kelvinlby.opencrafter.mixin.interaction;

import io.netty.channel.ChannelFutureListener;
import mod.kelvinlby.opencrafter.client.interaction.InteractionHooks;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Interactions: sees every packet the client sends, whoever sends it. The packet goes out unchanged. */
@Mixin(Connection.class)
abstract class ConnectionSendMixin {
	@Inject(method = "send(Lnet/minecraft/network/protocol/Packet;Lio/netty/channel/ChannelFutureListener;Z)V", at = @At("HEAD"))
	private void openCrafter$onSend(Packet<?> packet, @Nullable ChannelFutureListener listener, boolean flush, CallbackInfo ci) {
		InteractionHooks.onPacketSent(packet);
	}
}
