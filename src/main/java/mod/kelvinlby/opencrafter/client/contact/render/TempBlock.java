package mod.kelvinlby.opencrafter.client.contact.render;

import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4fc;

import java.util.function.Function;

/**
 * A block model drawn in the world for a while in place of (or after) the real block, moved as
 * the effect wants. Its identity is its optical-flow identity: one instance per animated block.
 */
public interface TempBlock {
	BlockPos pos();

	/** The state whose model is drawn. */
	BlockState state();

	/** Once per tick; false when the effect is over and the model should no longer be drawn. */
	boolean tick(double now);

	/** For the frame at {@code now}: each quad's block-local transform. Starts the effect's timeline. */
	Function<BakedQuad, Matrix4fc> pose(double now);

	/** It coincides with the real block in this frame (see {@code VisionHooks.beginWorldObject}). */
	boolean settled(double now);

	/** A frame recorded for optical flow drew it {@link #settled}. */
	default void noteSettledRecorded() {
	}

	/** Stopped early (effect switched off, world left): undo whatever it changed. */
	default void cancel() {
	}
}
