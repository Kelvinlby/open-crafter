package mod.kelvinlby.opencrafter.client.interaction.outcome;

import mod.kelvinlby.opencrafter.client.interaction.event.InteractionTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The world as it was right before an action: taken when {@code MultiPlayerGameMode} starts it,
 * before the client's own prediction changes anything, or when a raw packet is sent.
 */
public final class PreAction {
	private final ItemStack mainHand;
	private final ItemStack offHand;
	private final boolean screenOpen;
	private final boolean usingItem;
	private final @Nullable Entity vehicle;
	private final Map<BlockPos, BlockState> blocks = new HashMap<>();

	private PreAction(LocalPlayer player, boolean screenOpen) {
		this.mainHand = player.getMainHandItem().copy();
		this.offHand = player.getOffhandItem().copy();
		this.screenOpen = screenOpen;
		this.usingItem = player.isUsingItem();
		this.vehicle = player.getVehicle();
	}

	public static PreAction capture(Minecraft minecraft, LocalPlayer player, ClientLevel level, List<BlockPos> watched) {
		PreAction before = new PreAction(player, minecraft.gui.screen() != null);
		watched.forEach(pos -> before.blocks.put(pos.immutable(), level.getBlockState(pos)));
		return before;
	}

	/** The blocks an action on this target can change: the block and, for a use, the one in front of its face. */
	public static List<BlockPos> watchedFor(InteractionTarget target) {
		if (!(target instanceof InteractionTarget.Block block)) {
			return List.of();
		}
		return List.of(block.pos(), block.pos().relative(block.face()));
	}

	public ItemStack held(InteractionHand hand) {
		return hand == InteractionHand.MAIN_HAND ? mainHand : offHand;
	}

	/** The state before the action, or the current one for a position this snapshot did not watch. */
	public BlockState blockBefore(ClientLevel level, BlockPos pos) {
		BlockState before = blocks.get(pos);
		return before != null ? before : level.getBlockState(pos);
	}

	public boolean screenOpen() {
		return screenOpen;
	}

	public boolean usingItem() {
		return usingItem;
	}

	public @Nullable Entity vehicle() {
		return vehicle;
	}
}
