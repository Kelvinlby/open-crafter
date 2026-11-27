package mod.kelvinlby.opencrafter.client.interaction.event;

import java.util.List;

/**
 * What an interaction did to the world, decided within the outcome window.
 *
 * @param effect       the world confirmed a consequence (a block changed, an entity was hurt, a screen opened, ...)
 * @param reason       short wire name of what confirmed it ({@code block_changed}, {@code entity_hurt}, ...) or {@code none}
 * @param blockChanges blocks changed by it, in the order they were found (empty unless a block changed)
 */
public record Outcome(boolean effect, String reason, List<BlockChange> blockChanges) {
	public static final Outcome NONE = new Outcome(false, "none", List.of());

	public static Outcome of(String reason) {
		return new Outcome(true, reason, List.of());
	}

	public static Outcome blocks(List<BlockChange> changes) {
		return new Outcome(true, "block_changed", List.copyOf(changes));
	}
}
