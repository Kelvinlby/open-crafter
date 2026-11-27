package mod.kelvinlby.opencrafter.client.inventoryview;

import mod.kelvinlby.opencrafter.client.handview.anim.Easing;
import mod.kelvinlby.opencrafter.client.inventoryview.anim.Slide;
import mod.kelvinlby.opencrafter.core.config.ConfigStore;
import mod.kelvinlby.opencrafter.core.config.InventoryViewConfig;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import org.jspecify.annotations.Nullable;

/**
 * The backpack (player inventory screen) flying in from below the screen when it opens and back
 * down when it closes. After closing, the old screen stays drawn (render only, no input) until it
 * has left. Render thread only.
 */
final class BackpackFlight {
	private static final double NANOS_PER_TICK = 50_000_000.0;

	private final ConfigStore config;
	/** 0: fully below the screen, 1: in its place. */
	private final Slide slide = new Slide();
	private @Nullable Screen flying;
	private boolean closing;

	BackpackFlight(ConfigStore config) {
		this.config = config;
	}

	void onScreenChanged(@Nullable Screen old, @Nullable Screen current) {
		if (old == current) {
			// Closing a container sets no screen again (closeContainer, then Screen.onClose)
			return;
		}
		if (!settings().backpackOpen) {
			clear();
		} else if (current instanceof InventoryScreen) {
			open(current);
		} else if (old instanceof InventoryScreen && current == null) {
			close(old);
		} else {
			clear();
		}
	}

	/** How far down {@code screen} is drawn, in whole GUI pixels; 0 for every other screen. */
	int offset(Screen screen) {
		if (screen != flying || !settings().backpackOpen) {
			return 0;
		}
		return (int) Math.round((1 - shown()) * screen.height);
	}

	/** How much of the screen's dim backdrop shows, 0 to 1. */
	float dimStrength(Screen screen) {
		return screen == flying && settings().backpackOpen ? (float) shown() : 1;
	}

	/** The closed backpack still flying out, or null once it has left. */
	@Nullable Screen closingScreen() {
		if (closing && (slide.settled(now()) || !settings().backpackOpen)) {
			clear();
		}
		return closing ? flying : null;
	}

	private void open(Screen screen) {
		if (!closing) {
			slide.jumpTo(0);
		}
		flying = screen;
		closing = false;
		slide.moveTo(now(), 1, settings().backpackFlyInTicks);
	}

	private void close(Screen screen) {
		if (screen != flying) {
			// Opened before the animation was enabled: leave from its place
			slide.jumpTo(1);
		}
		flying = screen;
		closing = true;
		slide.moveTo(now(), 0, settings().backpackFlyOutTicks);
	}

	private void clear() {
		flying = null;
		closing = false;
		slide.jumpTo(0);
	}

	private double shown() {
		return Easing.smooth(slide.progress(now()));
	}

	private InventoryViewConfig settings() {
		return config.get().inventoryView;
	}

	private static double now() {
		return System.nanoTime() / NANOS_PER_TICK;
	}
}
