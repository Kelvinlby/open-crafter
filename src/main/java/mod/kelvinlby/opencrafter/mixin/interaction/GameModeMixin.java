package mod.kelvinlby.opencrafter.mixin.interaction;

import com.llamalad7.mixinextras.injector.wrapmethod.WrapMethod;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import mod.kelvinlby.opencrafter.client.interaction.InteractionHooks;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.component.PiercingWeapon;
import net.minecraft.world.item.component.SwingAnimation;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Interactions: marks each in-world action of the game mode, so the packets it sends carry the
 * world as it was before the client's prediction. Every call runs unchanged.
 */
@Mixin(MultiPlayerGameMode.class)
abstract class GameModeMixin {
	@WrapMethod(method = "useItemOn")
	private InteractionResult openCrafter$useItemOn(LocalPlayer player, InteractionHand hand, BlockHitResult hit, Operation<InteractionResult> original) {
		InteractionHooks.enterUseItemOn(hit);
		try {
			return original.call(player, hand, hit);
		} finally {
			InteractionHooks.exitGameMode();
		}
	}

	@WrapMethod(method = "startDestroyBlock")
	private boolean openCrafter$startDestroyBlock(BlockPos pos, Direction direction, Operation<Boolean> original) {
		InteractionHooks.enterBreak(pos);
		try {
			return original.call(pos, direction);
		} finally {
			InteractionHooks.exitGameMode();
		}
	}

	@WrapMethod(method = "continueDestroyBlock")
	private boolean openCrafter$continueDestroyBlock(BlockPos pos, Direction direction, Operation<Boolean> original) {
		InteractionHooks.enterBreak(pos);
		try {
			return original.call(pos, direction);
		} finally {
			InteractionHooks.exitGameMode();
		}
	}

	@WrapMethod(method = "stopDestroyBlock")
	private void openCrafter$stopDestroyBlock(Operation<Void> original) {
		InteractionHooks.enterOther();
		try {
			original.call();
		} finally {
			InteractionHooks.exitGameMode();
		}
	}

	@WrapMethod(method = "attack")
	private void openCrafter$attack(Player player, Entity entity, Operation<Void> original) {
		InteractionHooks.enterOther();
		try {
			original.call(player, entity);
		} finally {
			InteractionHooks.exitGameMode();
		}
	}

	@WrapMethod(method = "interact")
	private InteractionResult openCrafter$interact(Player player, Entity entity, EntityHitResult hit, InteractionHand hand, Operation<InteractionResult> original) {
		InteractionHooks.enterOther();
		try {
			return original.call(player, entity, hit, hand);
		} finally {
			InteractionHooks.exitGameMode();
		}
	}

	@WrapMethod(method = "useItem")
	private InteractionResult openCrafter$useItem(Player player, InteractionHand hand, Operation<InteractionResult> original) {
		InteractionHooks.enterOther();
		try {
			return original.call(player, hand);
		} finally {
			InteractionHooks.exitGameMode();
		}
	}

	@WrapMethod(method = "releaseUsingItem")
	private void openCrafter$releaseUsingItem(Player player, Operation<Void> original) {
		InteractionHooks.enterOther();
		try {
			original.call(player);
		} finally {
			InteractionHooks.exitGameMode();
		}
	}

	@WrapMethod(method = "piercingAttack")
	private void openCrafter$piercingAttack(SwingAnimation animation, PiercingWeapon weapon, Operation<Void> original) {
		InteractionHooks.enterOther();
		try {
			original.call(animation, weapon);
		} finally {
			InteractionHooks.exitGameMode();
		}
	}
}
