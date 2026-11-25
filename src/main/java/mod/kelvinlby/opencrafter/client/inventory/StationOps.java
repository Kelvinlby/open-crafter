package mod.kelvinlby.opencrafter.client.inventory;

import mod.kelvinlby.opencrafter.api.Args;
import mod.kelvinlby.opencrafter.api.ThreadAffinity;
import mod.kelvinlby.opencrafter.api.handler.HandlerRegistry;
import mod.kelvinlby.opencrafter.mixin.inventory.AnvilScreenAccessor;
import mod.kelvinlby.opencrafter.mixin.inventory.MerchantScreenAccessor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AnvilScreen;
import net.minecraft.client.gui.screens.inventory.BeaconScreen;
import net.minecraft.client.gui.screens.inventory.MerchantScreen;
import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ServerboundSetBeaconPacket;
import net.minecraft.util.StringUtil;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.BeaconMenu;
import net.minecraft.world.level.block.entity.BeaconBlockEntity;
import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.Optional;

/** Screen widgets that are not slots: villager trade list, beacon effects, anvil name field. */
final class StationOps {
	private StationOps() {
	}

	static void register(HandlerRegistry handlers) {
		handlers.register("inventory.select_trade", ThreadAffinity.MAIN, StationOps::selectTrade);
		handlers.register("inventory.beacon", ThreadAffinity.MAIN, StationOps::beacon);
		handlers.register("inventory.rename", ThreadAffinity.MAIN, StationOps::rename);
	}

	/** {@code index}: offer index in the full trade list (0 = top, independent of scrolling). */
	private static Object selectTrade(Args args) {
		MerchantScreen screen = Screens.require(MerchantScreen.class);
		int index = args.integer("index");
		int offers = screen.getMenu().getOffers().size();
		if (index < 0 || index >= offers) {
			throw new IllegalArgumentException("Trade " + index + " out of range [0, " + offers + ")");
		}
		MerchantScreenAccessor access = (MerchantScreenAccessor) screen;
		access.opencrafter$setShopItem(index);
		access.opencrafter$postButtonClick();
		return null;
	}

	/** {@code primary}, optional {@code secondary}: effect ids. Same as picking both and pressing Done. */
	private static Object beacon(Args args) {
		BeaconMenu menu = Screens.require(BeaconScreen.class).getMenu();
		Holder<MobEffect> primary = Ids.mobEffect(args.string("primary"));
		Holder<MobEffect> secondary = args.has("secondary") ? Ids.mobEffect(args.string("secondary")) : null;
		checkBeacon(menu, primary, secondary);
		Screens.player().connection.send(new ServerboundSetBeaconPacket(Optional.of(primary), Optional.ofNullable(secondary)));
		Screens.player().closeContainer();
		return null;
	}

	/** {@code name}: new item name ("" restores the default). Sets the anvil's text field like typing does. */
	private static Object rename(Args args) {
		AnvilScreen screen = Screens.require(AnvilScreen.class);
		String name = StringUtil.filterText(args.string("name"));
		if (name.length() > AnvilMenu.MAX_NAME_LENGTH) {
			throw new IllegalArgumentException("Name longer than " + AnvilMenu.MAX_NAME_LENGTH + " characters");
		}
		Slots.requireWithItem(screen.getMenu(), AnvilMenu.INPUT_SLOT);
		EditBox box = ((AnvilScreenAccessor) screen).opencrafter$nameBox();
		box.setValue(name);
		return Map.of("name", box.getValue());
	}

	private static void checkBeacon(BeaconMenu menu, Holder<MobEffect> primary, @Nullable Holder<MobEffect> secondary) {
		if (!menu.hasPayment()) {
			throw new IllegalStateException("Beacon needs a payment item in its slot");
		}
		if (!BeaconBlockEntity.validateEffects(primary, secondary, menu.getLevels())) {
			throw new IllegalArgumentException("Effects not available with a level " + menu.getLevels() + " pyramid");
		}
	}
}
