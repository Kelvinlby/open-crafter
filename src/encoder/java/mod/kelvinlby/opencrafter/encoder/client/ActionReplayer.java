package mod.kelvinlby.opencrafter.encoder.client;

import mod.kelvinlby.opencrafter.encoder.mixin.BlockStatePredictionHandlerAccessor;
import mod.kelvinlby.opencrafter.encoder.mixin.ClientLevelAccessor;
import mod.kelvinlby.opencrafter.encoder.mixin.MultiPlayerGameModeAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundAttackPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClickPacket;
import net.minecraft.network.protocol.game.ServerboundContainerClosePacket;
import net.minecraft.network.protocol.game.ServerboundInteractPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundPunchPacket;
import net.minecraft.network.protocol.game.ServerboundSetCarriedItemPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemOnPacket;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.EntityHitResult;
import org.jspecify.annotations.Nullable;

import java.util.List;

/**
 * Re-enacts the recorded client's actions through the game's own code paths: the game mode
 * predicts and sends exactly what the recorded client did (with the recorded prediction sequence
 * numbers, so the server's acknowledgements in the recording line up), and the local animations
 * (swings, item use, cracks, break particles, inventory clicks) follow as they did originally.
 * What has no client-side effect is just sent, so other mods still see it.
 */
final class ActionReplayer {
	private static final float JUST_BELOW_BREAK = 0.999F;

	private final Minecraft minecraft;
	private final ScreenPointer pointer = new ScreenPointer();
	private @Nullable BlockPos digPos;
	private @Nullable Direction digDirection;

	ActionReplayer(Minecraft minecraft) {
		this.minecraft = minecraft;
	}

	/** Where the client handles key bindings: re-enacts one tick's actions, in the order they were sent. */
	void replayTick(LocalPlayer player, List<Packet<?>> actions) {
		continueDigging(player, actions);
		for (Packet<?> packet : actions) {
			replay(player, packet);
		}
	}

	void reset() {
		digPos = null;
		digDirection = null;
	}

	private void replay(LocalPlayer player, Packet<?> packet) {
		MultiPlayerGameMode gameMode = minecraft.gameMode;
		switch (packet) {
			case ServerboundSetCarriedItemPacket carried -> player.getInventory().setSelectedSlot(carried.getSlot());
			case ServerboundPlayerActionPacket action -> replayPlayerAction(player, gameMode, action);
			case ServerboundPunchPacket punch -> {
				player.swing(InteractionHand.MAIN_HAND, player.getMainHandItem().getAttackAnimation(), false);
				player.connection.send(punch);
			}
			case ServerboundUseItemOnPacket use -> replayUseItemOn(player, gameMode, use);
			case ServerboundUseItemPacket use -> replayUseItem(player, gameMode, use);
			case ServerboundAttackPacket attack -> replayAttack(player, gameMode, attack);
			case ServerboundInteractPacket interact -> replayInteract(player, gameMode, interact);
			case ServerboundContainerClickPacket click -> replayContainerClick(player, gameMode, click);
			case ServerboundContainerClosePacket close -> replayContainerClose(player, close);
			case ServerboundClientCommandPacket command when command.getAction() == ServerboundClientCommandPacket.Action.PERFORM_RESPAWN -> player.respawn();
			default -> player.connection.send(packet);
		}
	}

	private void replayPlayerAction(LocalPlayer player, MultiPlayerGameMode gameMode, ServerboundPlayerActionPacket action) {
		switch (action.getAction()) {
			case START_DESTROY_BLOCK -> startDigging(gameMode, action);
			case STOP_DESTROY_BLOCK -> finishDigging(player, gameMode, action);
			case ABORT_DESTROY_BLOCK -> abortDigging(player, gameMode, action);
			case CHANGE_DESTROY_DIRECTION -> {
				digDirection = action.getDirection();
				player.connection.send(action);
			}
			case RELEASE_USE_ITEM -> gameMode.releaseUsingItem(player);
			case DROP_ITEM -> gameMode.dropItem(player, false);
			case DROP_ALL_ITEMS -> gameMode.dropItem(player, true);
			default -> player.connection.send(action);
		}
	}

	private void startDigging(MultiPlayerGameMode gameMode, ServerboundPlayerActionPacket action) {
		forceNextSequence(action.getSequence());
		gameMode.startDestroyBlock(action.getPos(), action.getDirection());
		boolean digging = gameMode.isDestroying();
		digPos = digging ? action.getPos() : null;
		digDirection = digging ? action.getDirection() : null;
	}

	/**
	 * A held attack on the block being broken (the recorded tick has a punch and no other dig action):
	 * the cracks grow as in vanilla, but the break itself waits for the recorded one.
	 */
	private void continueDigging(LocalPlayer player, List<Packet<?>> actions) {
		if (digPos == null || digDirection == null || !actions.contains(ServerboundPunchPacket.INSTANCE) || hasDigAction(actions)) {
			return;
		}
		MultiPlayerGameMode gameMode = minecraft.gameMode;
		MultiPlayerGameModeAccessor state = (MultiPlayerGameModeAccessor) gameMode;
		BlockState block = player.level().getBlockState(digPos);
		float progress = state.openCrafterEncoder$destroyProgress() + block.getDestroyProgress(player, player.level(), digPos);
		if (progress < 1.0F || state.openCrafterEncoder$destroyDelay() > 0) {
			gameMode.continueDestroyBlock(digPos, digDirection);
		} else {
			state.openCrafterEncoder$setDestroyProgress(Math.max(state.openCrafterEncoder$destroyProgress(), JUST_BELOW_BREAK));
			player.level().destroyBlockProgress(player.getId(), digPos, gameMode.getDestroyStage());
		}
	}

	/** The recorded client finished the break: completes it through the game mode, with its sequence. */
	private void finishDigging(LocalPlayer player, MultiPlayerGameMode gameMode, ServerboundPlayerActionPacket action) {
		BlockPos pos = action.getPos();
		forceNextSequence(action.getSequence());
		if (gameMode.isDestroying() && pos.equals(digPos)) {
			MultiPlayerGameModeAccessor state = (MultiPlayerGameModeAccessor) gameMode;
			state.openCrafterEncoder$setDestroyDelay(0);
			state.openCrafterEncoder$setDestroyProgress(1.0F);
			gameMode.continueDestroyBlock(pos, action.getDirection());
		} else {
			gameMode.destroyBlock(pos);
			player.connection.send(action);
		}
		digPos = null;
		digDirection = null;
	}

	private void abortDigging(LocalPlayer player, MultiPlayerGameMode gameMode, ServerboundPlayerActionPacket action) {
		if (gameMode.isDestroying()) {
			gameMode.stopDestroyBlock();
		} else {
			player.connection.send(action);
		}
		digPos = null;
		digDirection = null;
	}

	private void replayUseItemOn(LocalPlayer player, MultiPlayerGameMode gameMode, ServerboundUseItemOnPacket use) {
		ItemStack held = player.getItemInHand(use.hand());
		int countBefore = held.getCount();
		forceNextSequence(use.sequence());
		InteractionResult result = gameMode.useItemOn(player, use.hand(), use.hitResult());
		if (swingsHere(result)) {
			player.swing(use.hand(), held.getInteractAnimation(), false);
			if (!held.isEmpty() && (held.getCount() != countBefore || player.hasInfiniteMaterials())) {
				player.itemUsed(use.hand());
			}
		}
	}

	private void replayUseItem(LocalPlayer player, MultiPlayerGameMode gameMode, ServerboundUseItemPacket use) {
		ItemStack held = player.getItemInHand(use.hand());
		float yRot = player.getYRot();
		float xRot = player.getXRot();
		player.setYRot(use.yRot());
		player.setXRot(use.xRot());
		forceNextSequence(use.sequence());
		InteractionResult result = gameMode.useItem(player, use.hand());
		player.setYRot(yRot);
		player.setXRot(xRot);
		if (result instanceof InteractionResult.Success) {
			if (swingsHere(result)) {
				player.swing(use.hand(), held.getInteractAnimation(), false);
			}
			player.itemUsed(use.hand());
		}
	}

	private void replayAttack(LocalPlayer player, MultiPlayerGameMode gameMode, ServerboundAttackPacket attack) {
		Entity entity = player.level().getEntity(attack.entityId());
		if (entity == null) {
			player.connection.send(attack);
		} else {
			gameMode.attack(player, entity);
		}
	}

	private void replayInteract(LocalPlayer player, MultiPlayerGameMode gameMode, ServerboundInteractPacket interact) {
		Entity entity = player.level().getEntity(interact.entityId());
		if (entity == null) {
			player.connection.send(interact);
			return;
		}
		EntityHitResult hit = new EntityHitResult(entity, entity.position().add(interact.location()));
		InteractionResult result = gameMode.interact(player, entity, hit, interact.hand());
		if (swingsHere(result)) {
			player.swing(interact.hand(), player.getItemInHand(interact.hand()).getInteractAnimation(), false);
		}
	}

	/** Clicks happen in the open menu; the player's own inventory screen is opened first if the recorded client had it open. */
	private void replayContainerClick(LocalPlayer player, MultiPlayerGameMode gameMode, ServerboundContainerClickPacket click) {
		if (click.containerId() == player.inventoryMenu.containerId && minecraft.gui.screen() == null) {
			minecraft.gui.setScreen(new InventoryScreen(player));
		}
		if (player.containerMenu.containerId != click.containerId()) {
			player.connection.send(click);
			return;
		}
		pointer.pointAt(minecraft, click.slotNum());
		gameMode.handleContainerInput(click.containerId(), click.slotNum(), click.buttonNum(), click.containerInput(), player);
	}

	private void replayContainerClose(LocalPlayer player, ServerboundContainerClosePacket close) {
		if (minecraft.gui.screen() != null && player.containerMenu.containerId == close.getContainerId()) {
			player.closeContainer();
		} else {
			player.connection.send(close);
		}
	}

	/** The next prediction gets the recorded sequence number, so the recorded acknowledgements resolve it. */
	private void forceNextSequence(int sequence) {
		ClientLevel level = minecraft.level;
		if (level != null) {
			((BlockStatePredictionHandlerAccessor) ((ClientLevelAccessor) level).openCrafterEncoder$predictionHandler()).openCrafterEncoder$setCurrentSequenceNr(sequence - 1);
		}
	}

	private static boolean swingsHere(InteractionResult result) {
		return result instanceof InteractionResult.Success success && success.swingSource() == InteractionResult.SwingSource.PREDICTED;
	}

	private boolean hasDigAction(List<Packet<?>> actions) {
		for (Packet<?> packet : actions) {
			if (packet instanceof ServerboundPlayerActionPacket action && switch (action.getAction()) {
				case START_DESTROY_BLOCK, STOP_DESTROY_BLOCK, ABORT_DESTROY_BLOCK -> true;
				default -> false;
			}) {
				return true;
			}
		}
		return false;
	}
}
