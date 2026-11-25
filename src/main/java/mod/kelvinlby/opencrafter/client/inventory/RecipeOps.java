package mod.kelvinlby.opencrafter.client.inventory;

import mod.kelvinlby.opencrafter.api.Args;
import mod.kelvinlby.opencrafter.api.ThreadAffinity;
import mod.kelvinlby.opencrafter.api.handler.HandlerRegistry;
import net.minecraft.client.gui.screens.inventory.AbstractRecipeBookScreen;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.item.Item;

import java.util.Map;

/** Recipe-book placement ({@code ServerboundPlaceRecipePacket}), as clicking a recipe button does. */
final class RecipeOps {
	private RecipeOps() {
	}

	static void register(HandlerRegistry handlers) {
		handlers.register("inventory.place_recipe", ThreadAffinity.MAIN, RecipeOps::placeRecipe);
	}

	/** {@code item}: result item id; {@code max} (default false): shift-click, fill as many as possible. */
	private static Object placeRecipe(Args args) {
		RecipeBookMenu menu = recipeBookMenu();
		Item item = Ids.item(args.string("item"));
		RecipeFinder.Match match = RecipeFinder.find(Screens.player(), menu, item)
			.orElseThrow(() -> new IllegalArgumentException("No unlocked recipe for " + args.string("item") + " fits this screen"));
		Screens.minecraft().gameMode.handlePlaceRecipe(menu.containerId, match.entry().id(), args.bool("max", false));
		return Map.of("craftable", match.craftable());
	}

	private static RecipeBookMenu recipeBookMenu() {
		AbstractRecipeBookScreen<?> screen = Screens.require(AbstractRecipeBookScreen.class);
		RecipeBookMenu menu = screen.getMenu();
		if (menu != Screens.player().containerMenu) {
			throw new IllegalStateException("The recipe book's menu is not the open container");
		}
		return menu;
	}
}
