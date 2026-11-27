package mod.kelvinlby.opencrafter.client.interaction.event;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * One in-world action the local player sent to the server, whoever issued it (keyboard, the
 * backend, another mod). Immutable; its outcome arrives later through {@link InteractionListener}.
 *
 * @param id               increasing per game session, starting at 1
 * @param nanos            {@code System.nanoTime()} when the action was sent (comparable with frame {@code capture_nanos})
 * @param frameBefore      index of the last frame captured before the action, or -1
 * @param hand             the hand acting, null for actions without one (breaking, stabbing)
 * @param held             copy of what the hand held right before the action (empty when none)
 * @param contact          world-space point where the action meets its target, null when it has none
 * @param aim              the rotation the server sees for this action
 * @param crosshairAligned the target is what the rendered crosshair points at
 * @param fromGameMode     sent through {@code MultiPlayerGameMode} (false: a raw packet from some mod)
 */
public record InteractionEvent(
	long id,
	long nanos,
	long frameBefore,
	InteractionKind kind,
	@Nullable InteractionHand hand,
	ItemStack held,
	InteractionTarget target,
	@Nullable Vec3 contact,
	Aim aim,
	boolean crosshairAligned,
	boolean fromGameMode
) {
}
