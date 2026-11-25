package mod.kelvinlby.opencrafter.client.inventory;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import org.jspecify.annotations.Nullable;

/** Registry id parsing ({@code "minecraft:speed"} or {@code "speed"}) with readable errors. */
final class Ids {
	private Ids() {
	}

	static Identifier parse(String id) {
		Identifier identifier = Identifier.tryParse(id);
		if (identifier == null) {
			throw new IllegalArgumentException("Invalid id '" + id + "'");
		}
		return identifier;
	}

	static Holder<MobEffect> mobEffect(String id) {
		return holder(BuiltInRegistries.MOB_EFFECT, id, "mob effect");
	}

	static Item item(String id) {
		return holder(BuiltInRegistries.ITEM, id, "item").value();
	}

	static CreativeModeTab creativeTab(String id) {
		return holder(BuiltInRegistries.CREATIVE_MODE_TAB, id, "creative tab").value();
	}

	static String creativeTabId(CreativeModeTab tab) {
		return String.valueOf(BuiltInRegistries.CREATIVE_MODE_TAB.getKey(tab));
	}

	/** Menu type id; the player inventory and the creative picker have no registered type. */
	static @Nullable String menuType(AbstractContainerMenu menu) {
		try {
			return String.valueOf(BuiltInRegistries.MENU.getKey(menu.getType()));
		} catch (UnsupportedOperationException noType) {
			return null;
		}
	}

	private static <T> Holder<T> holder(Registry<T> registry, String id, String kind) {
		return registry.get(parse(id)).orElseThrow(() -> new IllegalArgumentException("Unknown " + kind + " '" + id + "'"));
	}
}
