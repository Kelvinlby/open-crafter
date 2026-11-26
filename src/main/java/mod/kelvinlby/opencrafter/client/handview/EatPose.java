package mod.kelvinlby.opencrafter.client.handview;

import mod.kelvinlby.opencrafter.client.handview.anim.EatCurve;
import mod.kelvinlby.opencrafter.core.config.handview.HandViewConfig;
import mod.kelvinlby.opencrafter.core.config.handview.SwingConfig;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUseAnimation;

/** Where the eating/drinking pose stands, following vanilla's or the smooth curve. */
final class EatPose {
	private EatPose() {
	}

	static boolean smooth(HandViewConfig config) {
		return config.swing.rightClick == SwingConfig.RightClickAnimation.SMOOTH;
	}

	/** How far {@code hand} is in the eating pose: 0 the normal pose, 1 fully in the middle. */
	static float progress(HandViewConfig config, LocalPlayer player, InteractionHand hand, float partialTicks) {
		if (config.eating.disableAnimation || !eating(player, hand)) {
			return 0;
		}
		return blend(config, player, partialTicks);
	}

	/** The eat curve right now for the item in use, whichever hand holds it. */
	static float blend(HandViewConfig config, LocalPlayer player, float partialTicks) {
		ItemStack stack = player.getUseItem();
		double remaining = player.getUseItemRemainingTicks() - partialTicks + 1;
		return EatCurve.blend(remaining, stack.getUseDuration(player), smooth(config));
	}

	private static boolean eating(LocalPlayer player, InteractionHand hand) {
		if (!player.isUsingItem() || player.getUseItemRemainingTicks() <= 0 || player.getUsedItemHand() != hand) {
			return false;
		}
		ItemUseAnimation animation = player.getUseItem().getUseAnimation();
		return animation == ItemUseAnimation.EAT || animation == ItemUseAnimation.DRINK;
	}
}
