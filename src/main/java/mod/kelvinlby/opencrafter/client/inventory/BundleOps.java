package mod.kelvinlby.opencrafter.client.inventory;

import mod.kelvinlby.opencrafter.api.Args;
import mod.kelvinlby.opencrafter.api.ThreadAffinity;
import mod.kelvinlby.opencrafter.api.handler.HandlerRegistry;
import net.minecraft.network.protocol.game.ServerboundSelectBundleItemPacket;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.BundleItem;
import net.minecraft.world.item.ItemStack;

/** Bundle item selection, as {@code BundleMouseActions} does on mouse-wheel over a bundle slot. */
final class BundleOps {
	private static final int NONE = -1;

	private BundleOps() {
	}

	static void register(HandlerRegistry handlers) {
		handlers.register("inventory.bundle_select", ThreadAffinity.MAIN, BundleOps::select);
	}

	/** {@code slot}: bundle slot; {@code index}: shown item index, -1 clears the selection. */
	private static Object select(Args args) {
		Slot slot = Slots.requireWithItem(Screens.containerScreen().getMenu(), args.integer("slot"));
		ItemStack bundle = slot.getItem();
		int index = args.integer("index");
		checkSelection(bundle, index);
		BundleItem.toggleSelectedItem(bundle, index);
		Screens.player().connection.send(new ServerboundSelectBundleItemPacket(slot.index, index));
		return null;
	}

	private static void checkSelection(ItemStack bundle, int index) {
		if (!bundle.is(ItemTags.BUNDLES)) {
			throw new IllegalArgumentException("Slot does not hold a bundle");
		}
		int shown = BundleItem.getNumberOfItemsToShow(bundle);
		if (index < NONE || index >= shown) {
			throw new IllegalArgumentException("Bundle index " + index + " out of range [-1, " + shown + ")");
		}
	}
}
