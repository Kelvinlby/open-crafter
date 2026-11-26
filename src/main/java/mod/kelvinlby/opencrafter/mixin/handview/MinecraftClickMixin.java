package mod.kelvinlby.opencrafter.mixin.handview;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import mod.kelvinlby.opencrafter.client.handview.HandViewHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.BlockHitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** Hand view: tells which click caused each of the local player's swings. Calls pass through unchanged. */
@Mixin(Minecraft.class)
abstract class MinecraftClickMixin {
	private static final String SWING = "Lnet/minecraft/client/player/LocalPlayer;swing(Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/item/component/SwingAnimation;Z)Z";

	@WrapOperation(method = {"startAttack", "continueAttack"}, at = @At(value = "INVOKE", target = SWING))
	private boolean openCrafter$leftSwing(LocalPlayer player, InteractionHand hand, SwingAnimation animation, boolean send, Operation<Boolean> original) {
		boolean started = original.call(player, hand, animation, send);
		HandViewHooks.onLeftSwing(started);
		return started;
	}

	@WrapOperation(method = "startUseItem", at = @At(value = "INVOKE",
		target = "Lnet/minecraft/client/multiplayer/MultiPlayerGameMode;useItemOn(Lnet/minecraft/client/player/LocalPlayer;Lnet/minecraft/world/InteractionHand;Lnet/minecraft/world/phys/BlockHitResult;)Lnet/minecraft/world/InteractionResult;"))
	private InteractionResult openCrafter$useItemOn(MultiPlayerGameMode gameMode, LocalPlayer player, InteractionHand hand, BlockHitResult hit,
		Operation<InteractionResult> original) {
		HandViewHooks.beforeUseItemOn(player, hand, hit);
		InteractionResult result = original.call(gameMode, player, hand, hit);
		HandViewHooks.afterUseItemOn();
		return result;
	}

	// startUseItem swings after using an entity (0), a block (1) and the item itself (2)
	@WrapOperation(method = "startUseItem", at = @At(value = "INVOKE", target = SWING, ordinal = 0))
	private boolean openCrafter$entitySwing(LocalPlayer player, InteractionHand hand, SwingAnimation animation, boolean send, Operation<Boolean> original) {
		boolean started = original.call(player, hand, animation, send);
		HandViewHooks.onInteractEntitySwing(started);
		return started;
	}

	@WrapOperation(method = "startUseItem", at = @At(value = "INVOKE", target = SWING, ordinal = 1))
	private boolean openCrafter$blockSwing(LocalPlayer player, InteractionHand hand, SwingAnimation animation, boolean send, Operation<Boolean> original) {
		boolean started = original.call(player, hand, animation, send);
		HandViewHooks.onUseBlockSwing(started);
		return started;
	}

	@WrapOperation(method = "startUseItem", at = @At(value = "INVOKE", target = SWING, ordinal = 2))
	private boolean openCrafter$itemSwing(LocalPlayer player, InteractionHand hand, SwingAnimation animation, boolean send, Operation<Boolean> original) {
		boolean started = original.call(player, hand, animation, send);
		HandViewHooks.onUseItemSwing(started);
		return started;
	}
}
