package mod.kelvinlby.opencrafter.client.inventory;

import mod.kelvinlby.opencrafter.api.Args;
import mod.kelvinlby.opencrafter.api.ThreadAffinity;
import mod.kelvinlby.opencrafter.api.handler.HandlerRegistry;
import mod.kelvinlby.opencrafter.mixin.inventory.CreativeScreenAccessor;
import net.fabricmc.fabric.api.client.creativetab.v1.FabricCreativeModeInventoryScreen;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;

import java.util.Locale;
import java.util.Map;

/**
 * Creative-screen controls that are not slot clicks: tabs (via Fabric's page-aware selector),
 * item list scrolling, search, and saved-hotbar toolbar keys. Slot clicks in the creative
 * screen (including the destroy-item slot) go through {@code inventory.click}.
 */
final class CreativeOps {
	private static final int HOTBARS = 9;

	private CreativeOps() {
	}

	static void register(HandlerRegistry handlers) {
		handlers.register("inventory.creative_tab", ThreadAffinity.MAIN, CreativeOps::selectTab);
		handlers.register("inventory.creative_scroll", ThreadAffinity.MAIN, CreativeOps::scroll);
		handlers.register("inventory.creative_search", ThreadAffinity.MAIN, CreativeOps::search);
		handlers.register("inventory.creative_hotbar", ThreadAffinity.MAIN, CreativeOps::hotbar);
	}

	/** {@code tab}: creative tab id, e.g. {@code minecraft:combat}, {@code minecraft:inventory}. */
	private static Object selectTab(Args args) {
		CreativeModeInventoryScreen screen = Screens.require(CreativeModeInventoryScreen.class);
		CreativeModeTab tab = Ids.creativeTab(args.string("tab"));
		if (!CreativeModeTabs.tabs().contains(tab)) {
			throw new IllegalArgumentException("Creative tab " + args.string("tab") + " is not shown");
		}
		fabric(screen).setSelectedTab(tab);
		return slotCount(screen);
	}

	/** {@code position}: 0 (top) to 1 (bottom) of the item list, like dragging the scrollbar. */
	private static Object scroll(Args args) {
		CreativeModeInventoryScreen screen = Screens.require(CreativeModeInventoryScreen.class);
		float position = (float) args.number("position");
		if (!(position >= 0.0F && position <= 1.0F)) {
			throw new IllegalArgumentException("position must be within [0, 1]");
		}
		if (!canScroll(screen)) {
			throw new IllegalStateException("The selected creative tab cannot scroll");
		}
		((CreativeScreenAccessor) screen).opencrafter$setScrollOffs(position);
		screen.getMenu().scrollTo(position);
		return null;
	}

	/** {@code text}: search query (selects the search tab first); "#tag" searches by tag. */
	private static Object search(Args args) {
		CreativeModeInventoryScreen screen = Screens.require(CreativeModeInventoryScreen.class);
		fabric(screen).setSelectedTab(CreativeModeTabs.searchTab());
		CreativeScreenAccessor access = (CreativeScreenAccessor) screen;
		EditBox box = access.opencrafter$searchBox();
		box.setValue(args.string("text"));
		access.opencrafter$refreshSearchResults();
		return Map.of("text", box.getValue(), "results", screen.getMenu().items.size());
	}

	/** {@code index}: 0-8; {@code action}: load | save. Same as the in-world hotbar activator keys. */
	private static Object hotbar(Args args) {
		LocalPlayer player = Screens.player();
		int index = args.integer("index");
		boolean load = parseHotbarAction(args.string("action"));
		checkHotbarAllowed(player, index);
		CreativeModeInventoryScreen.handleHotbarLoadOrSave(Screens.minecraft(), index, load, !load);
		return null;
	}

	private static boolean parseHotbarAction(String action) {
		return switch (action.toLowerCase(Locale.ROOT)) {
			case "load" -> true;
			case "save" -> false;
			default -> throw new IllegalArgumentException("Hotbar action must be load or save, got '" + action + "'");
		};
	}

	/** Vanilla only handles these keys in creative, with no screen open. */
	private static void checkHotbarAllowed(LocalPlayer player, int index) {
		if (index < 0 || index >= HOTBARS) {
			throw new IllegalArgumentException("Hotbar index must be 0-8, got " + index);
		}
		if (!player.hasInfiniteMaterials() || player.isSpectator()) {
			throw new IllegalStateException("Saved hotbars need creative mode");
		}
		if (Screens.current() != null) {
			throw new IllegalStateException("Saved hotbars are only handled with no screen open");
		}
	}

	private static boolean canScroll(CreativeModeInventoryScreen screen) {
		return fabric(screen).getSelectedTab().canScroll() && screen.getMenu().canScroll();
	}

	private static FabricCreativeModeInventoryScreen fabric(CreativeModeInventoryScreen screen) {
		return (FabricCreativeModeInventoryScreen) screen;
	}

	private static Map<String, Object> slotCount(CreativeModeInventoryScreen screen) {
		return Map.of("slot_count", screen.getMenu().slots.size());
	}
}
