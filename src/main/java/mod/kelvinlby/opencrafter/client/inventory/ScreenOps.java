package mod.kelvinlby.opencrafter.client.inventory;

import mod.kelvinlby.opencrafter.api.Args;
import mod.kelvinlby.opencrafter.api.ThreadAffinity;
import mod.kelvinlby.opencrafter.api.handler.HandlerRegistry;
import net.fabricmc.fabric.api.client.creativetab.v1.FabricCreativeModeInventoryScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.gui.screens.inventory.MenuAccess;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;

import java.util.HashMap;
import java.util.Map;

/** Opening/closing container screens and the minimal metadata needed to address slots. */
final class ScreenOps {
	private ScreenOps() {
	}

	static void register(HandlerRegistry handlers) {
		handlers.register("inventory.open", ThreadAffinity.MAIN, ScreenOps::open);
		handlers.register("inventory.close", ThreadAffinity.MAIN, ScreenOps::close);
		handlers.register("inventory.info", ThreadAffinity.MAIN, ScreenOps::info);
	}

	/** The inventory key's in-world action: own inventory (creative screen in creative) or a ridden mount's. */
	private static Object open(Args args) {
		LocalPlayer player = Screens.player();
		Minecraft minecraft = Screens.minecraft();
		if (Screens.current() != null) {
			throw new IllegalStateException("A screen is already open (" + Screens.describe(Screens.current()) + ")");
		}
		if (minecraft.gameMode.isServerControlledInventory()) {
			player.sendOpenInventory();
		} else {
			minecraft.getTutorial().onOpenInventory();
			minecraft.gui.setScreen(new InventoryScreen(player));
		}
		return null;
	}

	/** What Esc does on a container screen: {@code onClose()}, which also tells the server. */
	private static Object close(Args args) {
		Screen screen = Screens.current();
		if (!(screen instanceof MenuAccess<?>)) {
			throw new IllegalStateException("No container screen open (current screen: " + Screens.describe(screen) + ")");
		}
		screen.onClose();
		return null;
	}

	/** Only what is needed to address slots; contents are read visually. */
	private static Object info(Args args) {
		Map<String, Object> info = new HashMap<>();
		Screen screen = Screens.minecraft().player == null ? null : Screens.current();
		info.put("screen", screen == null ? null : Screens.describe(screen));
		if (screen instanceof MenuAccess<?> access) {
			putMenu(info, access.getMenu());
		}
		if (screen instanceof CreativeModeInventoryScreen creative) {
			info.put("creative_tab", Ids.creativeTabId(((FabricCreativeModeInventoryScreen) creative).getSelectedTab()));
		}
		return info;
	}

	private static void putMenu(Map<String, Object> info, AbstractContainerMenu menu) {
		info.put("menu_type", Ids.menuType(menu));
		info.put("container_id", menu.containerId);
		info.put("slot_count", menu.slots.size());
		info.put("carried_empty", menu.getCarried().isEmpty());
	}
}
