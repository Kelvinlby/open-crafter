package mod.kelvinlby.opencrafter.client.interaction.outcome;

import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;

/**
 * The world as a probe sees it now.
 *
 * @param hurtEntities ids of entities the server reported hurt since the action was sent
 */
record ProbeContext(Minecraft minecraft, LocalPlayer player, ClientLevel level, IntSet hurtEntities) {
	/** The part shared by every pending action in one check. */
	record Base(Minecraft minecraft, LocalPlayer player, ClientLevel level) {
		ProbeContext with(IntSet hurtEntities) {
			return new ProbeContext(minecraft, player, level, hurtEntities);
		}
	}
}
