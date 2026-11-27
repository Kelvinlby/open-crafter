package mod.kelvinlby.opencrafter.client.interaction.capture;

import mod.kelvinlby.opencrafter.client.interaction.event.Aim;
import mod.kelvinlby.opencrafter.client.interaction.event.InteractionKind;
import mod.kelvinlby.opencrafter.client.interaction.event.InteractionTarget;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * What one serverbound packet says about an action, before any client-side context is added.
 *
 * @param location the packet's own hit location: absolute for block hits, entity-relative for entity interactions
 * @param aim      a rotation the packet carries itself (only {@code use_item} does)
 */
record SentAction(
	InteractionKind kind,
	@Nullable InteractionHand hand,
	InteractionTarget target,
	@Nullable Vec3 location,
	@Nullable Aim aim
) {
}
