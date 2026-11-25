package mod.kelvinlby.opencrafter.client.inventory;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.player.LocalPlayer;
import org.jspecify.annotations.Nullable;

/** Resolves the current screen/player and rejects ops the open screen cannot perform. */
final class Screens {
	private Screens() {
	}

	static Minecraft minecraft() {
		return Minecraft.getInstance();
	}

	static LocalPlayer player() {
		LocalPlayer player = minecraft().player;
		if (player == null || minecraft().gameMode == null) {
			throw new IllegalStateException("Not in a world");
		}
		return player;
	}

	static @Nullable Screen current() {
		return minecraft().gui.screen();
	}

	/** The open screen as {@code type}, or an error naming what is open instead. */
	static <T> T require(Class<T> type) {
		player();
		Screen screen = current();
		if (!type.isInstance(screen)) {
			throw new IllegalStateException("Op needs a " + type.getSimpleName() + " open (current screen: " + describe(screen) + ")");
		}
		return type.cast(screen);
	}

	static AbstractContainerScreen<?> containerScreen() {
		return require(AbstractContainerScreen.class);
	}

	/** A menu screen whose menu is the one the server knows about (not the creative item picker). */
	static MenuAccess<?> serverMenuScreen() {
		MenuAccess<?> screen = require(MenuAccess.class);
		if (screen.getMenu() != player().containerMenu) {
			throw new IllegalStateException("The open screen's menu is client-side only (" + describe(current()) + ")");
		}
		return screen;
	}

	static String describe(@Nullable Screen screen) {
		return screen == null ? "none" : screen.getClass().getSimpleName();
	}
}
