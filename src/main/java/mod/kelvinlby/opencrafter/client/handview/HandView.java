package mod.kelvinlby.opencrafter.client.handview;

import mod.kelvinlby.opencrafter.core.config.ConfigStore;
import mod.kelvinlby.opencrafter.core.config.handview.HandViewConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import org.jspecify.annotations.Nullable;

/** The hand view feature's state: its settings and the per-tick trackers the render hooks read. */
final class HandView {
	final SwingAnimator swings = new SwingAnimator();
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
		placement = PlacementProbe.before(level, player.getItemInHand(hand), hit);
	}

	void afterUseItemOn(ClientLevel level) {
		swings.notePlacement(placement == null ? null : placement.placedCentre(level));
		placement = null;
	}
}
