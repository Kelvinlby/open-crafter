package mod.kelvinlby.opencrafter.client.contact.hinge;

import mod.kelvinlby.opencrafter.client.contact.anim.Easing;
import mod.kelvinlby.opencrafter.client.contact.anim.PartMotion;
import mod.kelvinlby.opencrafter.client.contact.anim.Timeline;
import mod.kelvinlby.opencrafter.client.contact.render.TempBlock;
import mod.kelvinlby.opencrafter.client.vision.VisionHooks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.resources.model.geometry.BakedQuad;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;

import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A toggled block's new model swinging (or sliding) from its old pose to the new one, drawn in
 * place of the real block, which is left out of the chunk mesh meanwhile. Once there, it stays
 * on top of the real block until a frame recorded for flow saw both, so the flow hands its pixels
 * over to the block.
 */
final class SwingingBlock implements TempBlock {
	private enum Phase {
		/** Moving; the real block is hidden. */
		SWING,
		/** At the new pose, drawn on the real block, which is shown again. */
		SETTLING,
		DONE
	}

	/**
	 * Longest stay on the real block waiting for a recorded frame (ticks): covers frame rates down to
	 * 1 Hz (software rendering with a shader pack). Both coincide, so staying longer shows nothing.
	 */
	private static final double MAX_SETTLE_TICKS = 20;

	private final BlockPos pos;
	private final BlockState state;
	private final Map<BakedQuad, PartMotion> motions;
	private final HiddenBlocks hidden;
	private final Timeline timeline;
	private Phase phase = Phase.SWING;
	private double settlingSince;
	private boolean settledRecorded;

	SwingingBlock(BlockPos pos, BlockState state, List<BakedQuad> quads, List<PartMotion> motions, HiddenBlocks hidden, double durationTicks,
		double now) {
		this.pos = pos.immutable();
		this.state = state;
		this.motions = byQuad(quads, motions);
		this.hidden = hidden;
		this.timeline = new Timeline(durationTicks, now);
	}

	/** Hides the real block; call once when it starts. */
	void start(ClientLevel level) {
		hidden.hide(level, pos);
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
		if (phase == Phase.SWING && timeline.finished(now)) {
			phase = Phase.SETTLING;
			settlingSince = now;
			reveal();
		} else if (phase == Phase.SETTLING && settledLongEnough(now)) {
			phase = Phase.DONE;
		}
		return phase != Phase.DONE;
	}

	@Override
	public Function<BakedQuad, Matrix4fc> pose(double now) {
		double remaining = phase == Phase.SWING ? 1.0 - Easing.smooth(timeline.progress(now)) : 0.0;
		Map<PartMotion, Matrix4f> perPart = new IdentityHashMap<>();
		return quad -> perPart.computeIfAbsent(motions.getOrDefault(quad, PartMotion.STILL), motion -> motion.at(remaining));
	}

	@Override
	public boolean settled(double now) {
		return phase == Phase.SETTLING && now - settlingSince >= 1;
	}

	@Override
	public void noteSettledRecorded() {
		settledRecorded = true;
	}

	@Override
	public void cancel() {
		if (phase == Phase.SWING) {
			reveal();
		}
		phase = Phase.DONE;
	}

	/** The real block is meshed again from the tick after; until then both are drawn. */
	private boolean settledLongEnough(double now) {
		double settling = now - settlingSince;
		return settling >= 1 && (!VisionHooks.tracksMotion() || settledRecorded || settling >= MAX_SETTLE_TICKS);
	}

	private void reveal() {
		ClientLevel level = Minecraft.getInstance().level;
		if (level != null) {
			hidden.reveal(level, pos);
		}
	}

	private static Map<BakedQuad, PartMotion> byQuad(List<BakedQuad> quads, List<PartMotion> motions) {
		Map<BakedQuad, PartMotion> map = new IdentityHashMap<>();
		for (int i = 0; i < quads.size(); i++) {
			map.put(quads.get(i), motions.get(i));
		}
		return map;
	}
}
