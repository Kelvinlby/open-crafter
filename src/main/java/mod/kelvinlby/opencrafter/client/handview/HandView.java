package mod.kelvinlby.opencrafter.client.handview;

import mod.kelvinlby.opencrafter.core.config.ConfigStore;
import mod.kelvinlby.opencrafter.core.config.handview.HandViewConfig;
import mod.kelvinlby.opencrafter.core.config.handview.SwingConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/** The hand view feature's state: its settings and the per-tick trackers the render hooks read. */
final class HandView {
	final SwingAnimator swings = new SwingAnimator();
	final BlockFlights flights = new BlockFlights();
	final ServerRotation rotation = new ServerRotation();
	final HandPose pose = new HandPose();
	final EatRelease eats = new EatRelease();
	private final ConfigStore config;
	private @Nullable PlacementProbe placement;

	HandView(ConfigStore config) {
		this.config = config;
	}

	/** The settings while the global toggle is on, else null: every hook then leaves vanilla alone. */
	@Nullable HandViewConfig active() {
		HandViewConfig handView = config.get().handView;
		return handView.enabled ? handView : null;
	}

	void onTickStart(Minecraft minecraft) {
		HandViewConfig active = active();
		swings.tick(minecraft, active == null ? null : active.swing);
		flights.tick(active != null);
		eats.tick();
	}

	void onTickEnd(Minecraft minecraft) {
		HandViewConfig active = active();
		if (active != null && active.serverRotations && minecraft.player != null) {
			rotation.tick(minecraft.player);
		} else {
			rotation.reset();
		}
	}

	void beforeUseItemOn(ClientLevel level, Player player, InteractionHand hand, BlockHitResult hit) {
		placement = PlacementProbe.before(level, player.getItemInHand(hand), hand, hit);
	}

	/** A placed block launches its flight right away (independent of the swing), and tells the hand what happened. */
	void afterUseItemOn(ClientLevel level, LocalPlayer player, HandViewConfig config) {
		PlacedBlock placed = placement == null ? null : placement.placed(level);
		placement = null;
		if (placed != null && config.swing.rightClick == SwingConfig.RightClickAnimation.SMOOTH) {
			swings.notePlacement(flights.launch(player, level, placed, config.swing.swingTicks));
		}
	}
}
