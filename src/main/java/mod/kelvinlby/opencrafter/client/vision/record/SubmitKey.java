package mod.kelvinlby.opencrafter.client.vision.record;

import net.minecraft.world.InteractionHand;

/**
 * Identifies one submitted piece of geometry across frames.
 *
 * @param owner         the object it belongs to: {@link EntityOwner}, {@link BlockEntityOwner},
 *                      {@link HandOwner}, {@link #HAND_OWNER}, a hand object such as a flying block
 *                      (identity) or a particle instance (identity)
 * @param discriminator what kind of submit it is (the model instance for models, else the feature type)
 * @param ordinal       how many submits with the same owner and discriminator came before it this frame
 */
public record SubmitKey(RecordPhase phase, Object owner, Object discriminator, int ordinal) {
	public static final Object HAND_OWNER = new Object() {
		@Override
		public String toString() {
			return "first-person hand";
		}
	};

	public record EntityOwner(int entityId) {
	}

	public record BlockEntityOwner(long blockPos) {
	}

	/** One first-person hand's arm and held item: hands never share ordinals, so one vanishing cannot shift the other. */
	public record HandOwner(InteractionHand hand) {
	}

	/** The same submit, owned by {@code newOwner}. */
	public SubmitKey withOwner(Object newOwner) {
		return new SubmitKey(phase, newOwner, discriminator, ordinal);
	}
}
