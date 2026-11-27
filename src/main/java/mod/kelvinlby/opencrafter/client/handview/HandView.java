package mod.kelvinlby.opencrafter.client.handview;

import mod.kelvinlby.opencrafter.core.config.ConfigStore;
import mod.kelvinlby.opencrafter.core.config.handview.HandViewConfig;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

/** The hand view feature's state: its settings and the per-tick trackers the render hooks read. */
final class HandView {
	final SwingAnimator swings = new SwingAnimator();
	final BlockFlights flights = new BlockFlights();
	final ServerRotation rotation = new ServerRotation();
	final ContactAim aims = new ContactAim();
	final HandPose pose = new HandPose();
	final EatRelease eats = new EatRelease();
	final HandActions actions;
	private final ConfigStore config;

	HandView(ConfigStore config) {
		this.config = config;
		this.actions = new HandActions(this::active, swings, flights, aims);
	}

	/** The settings while the global toggle is on, else null: every hook then leaves vanilla alone. */
	@Nullable HandViewConfig active() {
		HandViewConfig handView = config.get().handView;
		return handView.enabled ? handView : null;
	}

	void onTickStart(Minecraft minecraft) {
		HandViewConfig active = active();
		actions.tick(minecraft);
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
}
