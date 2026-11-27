package mod.kelvinlby.opencrafter.client.contact.shrink;

import mod.kelvinlby.opencrafter.client.contact.anim.Easing;
import mod.kelvinlby.opencrafter.client.contact.anim.Timeline;
import mod.kelvinlby.opencrafter.client.contact.render.TempBlock;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

import java.util.function.Function;

/** A broken block's model shrinking into the item it dropped (or to its centre when nothing dropped). */
final class ShrinkingBlock implements TempBlock {
	/** Below this size the model is gone. */
	private static final double MIN_SCALE = 0.02;

	private final BlockPos pos;
	private final BlockState state;
	private final Timeline timeline;
	private final DropTarget target;

	ShrinkingBlock(BlockPos pos, BlockState state, double durationTicks, double now) {
		this.pos = pos.immutable();
		this.state = state;
		this.timeline = new Timeline(durationTicks, now);
		this.target = new DropTarget(this.pos);
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
		double progress = timeline.progress(now);
		Vector3f centre = centre(progress, target.local(partialTick(now)));
		Matrix4f shrunk = shrunk(centre, scale(progress));
		return quad -> shrunk;
	}

	@Override
	public boolean settled(double now) {
		return false;
	}

	/** Full size at the start, eased down to nothing. */
	static double scale(double progress) {
		return Math.max(MIN_SCALE, 1.0 - Easing.smooth(progress));
	}

	/** The shrinking model's centre: the block's own at the start, eased onto the target. */
	static Vector3f centre(double progress, Vector3f target) {
		return new Vector3f(0.5F, 0.5F, 0.5F).lerp(target, (float) Easing.smooth(progress));
	}

	/** Effect-clock ticks are whole, so the fraction of {@code now} is the frame's partial tick. */
	private static float partialTick(double now) {
		return (float) (now - Math.floor(now));
	}

	/** The model scaled about its centre, that centre moved to {@code centre}. */
	private static Matrix4f shrunk(Vector3f centre, double scale) {
		return new Matrix4f().translate(centre).scale((float) scale).translate(-0.5F, -0.5F, -0.5F);
	}
}
