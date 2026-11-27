package mod.kelvinlby.opencrafter.client.contact.shrink;

import mod.kelvinlby.opencrafter.client.contact.anim.Easing;
import mod.kelvinlby.opencrafter.client.contact.anim.Timeline;
import mod.kelvinlby.opencrafter.client.contact.render.TempBlock;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.function.Function;

/** A broken block's model shrinking to its centre, where the block was. */
final class ShrinkingBlock implements TempBlock {
	/** Below this size the model is gone. */
	private static final double MIN_SCALE = 0.02;

	private final BlockPos pos;
	private final BlockState state;
	private final Timeline timeline;

	ShrinkingBlock(BlockPos pos, BlockState state, double durationTicks, double now) {
		this.pos = pos.immutable();
		this.state = state;
		this.timeline = new Timeline(durationTicks, now);
	}

	@Override
	public BlockPos pos() {
		return pos;
	}

	@Override
	public BlockState state() {
		return state;
	}

	@Override
	public boolean tick(double now) {
		return !timeline.finished(now);
	}

	@Override
	public Function<BakedQuad, Matrix4fc> pose(double now) {
		Matrix4f scaled = shrunk(scale(timeline.progress(now)));
		return quad -> scaled;
	}

	@Override
	public boolean settled(double now) {
		return false;
	}

	/** Full size at the start, eased down to nothing. */
	static double scale(double progress) {
		return Math.max(MIN_SCALE, 1.0 - Easing.smooth(progress));
	}

	private static Matrix4f shrunk(double scale) {
		float s = (float) scale;
		return new Matrix4f().translate(0.5F, 0.5F, 0.5F).scale(s).translate(-0.5F, -0.5F, -0.5F);
	}
}
