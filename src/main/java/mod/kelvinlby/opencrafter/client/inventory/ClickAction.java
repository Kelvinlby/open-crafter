package mod.kelvinlby.opencrafter.client.inventory;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerInput;

import java.util.Locale;
import java.util.function.IntPredicate;

/** Backend names for {@link ContainerInput}, with the button values the vanilla screen can produce. */
enum ClickAction {
	PICKUP(ContainerInput.PICKUP, true, button -> button == 0 || button == 1, "0 (left) or 1 (right)"),
	QUICK_MOVE(ContainerInput.QUICK_MOVE, false, button -> button == 0 || button == 1, "0 or 1"),
	SWAP(ContainerInput.SWAP, false, button -> button >= 0 && button < 9 || button == 40, "0-8 (hotbar) or 40 (offhand)"),
	CLONE(ContainerInput.CLONE, false, button -> button >= 0 && button <= 2, "0-2"),
	THROW(ContainerInput.THROW, true, button -> button == 0 || button == 1, "0 (one item) or 1 (whole stack)"),
	QUICK_CRAFT(ContainerInput.QUICK_CRAFT, true, ClickAction::isQuickCraftMask, "a quick-craft mask (stage | type << 2, stage and type 0-2)"),
	PICKUP_ALL(ContainerInput.PICKUP_ALL, false, button -> button == 0, "0");

	private final ContainerInput input;
	private final boolean allowsOutside;
	private final IntPredicate validButton;
	private final String buttonHelp;

	ClickAction(ContainerInput input, boolean allowsOutside, IntPredicate validButton, String buttonHelp) {
		this.input = input;
		this.allowsOutside = allowsOutside;
		this.validButton = validButton;
		this.buttonHelp = buttonHelp;
	}

	static ClickAction parse(String name) {
		try {
			return valueOf(name.toUpperCase(Locale.ROOT));
		} catch (IllegalArgumentException e) {
			throw new IllegalArgumentException("Unknown click action '" + name + "' (pickup, quick_move, swap, clone, throw, quick_craft, pickup_all)");
		}
	}

	ContainerInput input() {
		return input;
	}

	boolean allowsOutside() {
		return allowsOutside;
	}

	String wireName() {
		return name().toLowerCase(Locale.ROOT);
	}

	void checkButton(int button) {
		if (!validButton.test(button)) {
			throw new IllegalArgumentException("Button for " + wireName() + " must be " + buttonHelp + ", got " + button);
		}
	}

	private static boolean isQuickCraftMask(int mask) {
		int stage = AbstractContainerMenu.getQuickcraftHeader(mask);
		int type = AbstractContainerMenu.getQuickcraftType(mask);
		return mask >= 0 && mask == AbstractContainerMenu.getQuickcraftMask(stage, type) && stage <= 2 && type <= 2;
	}
}
