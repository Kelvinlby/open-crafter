package mod.kelvinlby.opencrafter.client.inventory;

import mod.kelvinlby.opencrafter.api.Args;
import mod.kelvinlby.opencrafter.api.ThreadAffinity;
import mod.kelvinlby.opencrafter.api.handler.HandlerRegistry;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.LecternMenu;
import net.minecraft.world.inventory.LoomMenu;
import net.minecraft.world.inventory.StonecutterMenu;

/**
 * {@code ServerboundContainerButtonClickPacket}: enchanting options, stonecutter recipes, loom
 * patterns, lectern page/take-book. Mirrors each screen: predict locally, play its sound, send.
 */
final class MenuButtonOps {
	private static final int LECTERN_PREVIOUS_PAGE = 1;
	private static final int LECTERN_NEXT_PAGE = 2;
	private static final int LECTERN_TAKE_BOOK = 3;
	private static final int LECTERN_PAGE_BASE = 100;

	private MenuButtonOps() {
	}

	static void register(HandlerRegistry handlers) {
		handlers.register("inventory.button", ThreadAffinity.MAIN, MenuButtonOps::button);
	}

	/** {@code id}: the menu-specific button id. */
	private static Object button(Args args) {
		AbstractContainerMenu menu = Screens.serverMenuScreen().getMenu();
		int id = args.integer("id");
		if (menu instanceof LecternMenu) {
			checkLecternButton(id);
		} else {
			predictButton(menu, id);
		}
		playSelectSound(menu);
		Screens.minecraft().gameMode.handleInventoryButtonClick(menu.containerId, id);
		return null;
	}

	/** Enchanting, stonecutter and loom screens only send ids their menu accepts locally. */
	private static void predictButton(AbstractContainerMenu menu, int id) {
		if (!menu.clickMenuButton(Screens.player(), id)) {
			throw new IllegalArgumentException("Button " + id + " is not available in " + Screens.describe(Screens.current()));
		}
	}

	/** The lectern screen sends without local prediction; it offers take-book only to players who may build. */
	private static void checkLecternButton(int id) {
		boolean page = id == LECTERN_PREVIOUS_PAGE || id == LECTERN_NEXT_PAGE || id >= LECTERN_PAGE_BASE;
		boolean take = id == LECTERN_TAKE_BOOK && Screens.player().mayBuild();
		if (!page && !take) {
			throw new IllegalArgumentException("Lectern buttons: 1 previous, 2 next, 3 take book (if allowed), 100+n go to page n; got " + id);
		}
	}

	private static void playSelectSound(AbstractContainerMenu menu) {
		if (menu instanceof StonecutterMenu) {
			Screens.minecraft().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_STONECUTTER_SELECT_RECIPE, 1.0F));
		} else if (menu instanceof LoomMenu) {
			Screens.minecraft().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_LOOM_SELECT_PATTERN, 1.0F));
		}
	}
}
