package mod.kelvinlby.opencrafter.client.interaction.event;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/** Which object an interaction acts on. */
public sealed interface InteractionTarget {
	/** No object: using an item in the air, a stab along the view, releasing a use. */
	record None() implements InteractionTarget {
	}

	record Entity(int entityId) implements InteractionTarget {
	}

	record Block(BlockPos pos, Direction face) implements InteractionTarget {
	}

	InteractionTarget NONE = new None();
}
