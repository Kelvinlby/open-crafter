package mod.kelvinlby.opencrafter.client.inventory;

import net.minecraft.client.ClientRecipeBook;
import net.minecraft.client.gui.screens.recipebook.RecipeCollection;
import net.minecraft.client.gui.screens.recipebook.SearchRecipeBookCategory;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.entity.player.StackedItemContents;
import net.minecraft.world.inventory.AbstractCraftingMenu;
import net.minecraft.world.inventory.RecipeBookMenu;
import net.minecraft.world.inventory.RecipeBookType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.crafting.display.FurnaceRecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplay;
import net.minecraft.world.item.crafting.display.RecipeDisplayEntry;
import net.minecraft.world.item.crafting.display.ShapedCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.ShapelessCraftingRecipeDisplay;
import net.minecraft.world.item.crafting.display.SlotDisplayContext;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/** Looks up the unlocked recipe-book entry the recipe book would show for a result item. */
final class RecipeFinder {
	record Match(RecipeDisplayEntry entry, boolean craftable) {
	}

	private RecipeFinder() {
	}

	/** Best entry producing {@code item} that fits {@code menu}; craftable ones first, like the book's sort. */
	static Optional<Match> find(LocalPlayer player, RecipeBookMenu menu, Item item) {
		StackedItemContents contents = stackedContents(player, menu);
		ContextMap context = SlotDisplayContext.fromLevel(player.level());
		return entries(player.getRecipeBook(), menu.getRecipeBookType()).stream()
			.filter(entry -> fits(menu, entry.display()))
			.filter(entry -> produces(entry, context, item))
			.map(entry -> new Match(entry, entry.canCraft(contents)))
			.min(Comparator.comparing(match -> !match.craftable()));
	}

	/** What {@code RecipeBookComponent} counts as available: player inventory plus the grid. */
	private static StackedItemContents stackedContents(LocalPlayer player, RecipeBookMenu menu) {
		StackedItemContents contents = new StackedItemContents();
		player.getInventory().fillStackedContents(contents);
		menu.fillCraftSlotsStackedContents(contents);
		return contents;
	}

	private static List<RecipeDisplayEntry> entries(ClientRecipeBook book, RecipeBookType type) {
		return book.getCollection(searchCategory(type)).stream()
			.map(RecipeCollection::getRecipes)
			.flatMap(List::stream)
			.toList();
	}

	private static SearchRecipeBookCategory searchCategory(RecipeBookType type) {
		return switch (type) {
			case CRAFTING -> SearchRecipeBookCategory.CRAFTING;
			case FURNACE -> SearchRecipeBookCategory.FURNACE;
			case BLAST_FURNACE -> SearchRecipeBookCategory.BLAST_FURNACE;
			case SMOKER -> SearchRecipeBookCategory.SMOKER;
		};
	}

	/** {@code CraftingRecipeBookComponent.canDisplay} / {@code FurnaceRecipeBookComponent} filter. */
	private static boolean fits(RecipeBookMenu menu, RecipeDisplay display) {
		if (menu instanceof AbstractCraftingMenu crafting) {
			return fitsGrid(crafting.getGridWidth(), crafting.getGridHeight(), display);
		}
		return display instanceof FurnaceRecipeDisplay;
	}

	private static boolean fitsGrid(int width, int height, RecipeDisplay display) {
		return switch (display) {
			case ShapedCraftingRecipeDisplay shaped -> width >= shaped.width() && height >= shaped.height();
			case ShapelessCraftingRecipeDisplay shapeless -> width * height >= shapeless.ingredients().size();
			default -> false;
		};
	}

	private static boolean produces(RecipeDisplayEntry entry, ContextMap context, Item item) {
		return entry.resultItems(context).stream().anyMatch(stack -> stack.is(item));
	}
}
