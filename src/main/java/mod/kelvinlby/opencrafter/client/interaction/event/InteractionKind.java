package mod.kelvinlby.opencrafter.client.interaction.event;

/** What the player did to the world, as the server is told about it. */
public enum InteractionKind {
	ATTACK_ENTITY("attack_entity"),
	INTERACT_ENTITY("interact_entity"),
	USE_ON_BLOCK("use_on_block"),
	BREAK_START("break_start"),
	BREAK_DONE("break_done"),
	BREAK_ABORT("break_abort"),
	STAB("stab"),
	USE_ITEM("use_item"),
	RELEASE_USE("release_use");

	private final String wireName;

	InteractionKind(String wireName) {
		this.wireName = wireName;
	}

	public String wireName() {
		return wireName;
	}
}
