package mod.kelvinlby.opencrafter.client.handview;

import com.mojang.blaze3d.vertex.PoseStack;
import mod.kelvinlby.opencrafter.client.handview.anim.RightClickPace;
import mod.kelvinlby.opencrafter.client.vision.VisionHooks;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Placed 3D blocks flying from the hand into place. Every placement launches its own
 * {@link MorphFlight}, independent of the hand's swing and of other flights, and its block is drawn
 * only once the flight lands or leaves the screen. A flight into a block hidden behind others still
 * runs to the end.
 */
final class BlockFlights {
	/** A flight lasts this share of a swing, at the right click pace. */
	private static final double SWING_SHARE = 0.5;
	/** A hand pose older than this (ticks) is stale: the hand was not drawn holding an item since. */
	private static final long HAND_POSE_MAX_AGE = 1;

	private final HeldBackBlocks heldBack = new HeldBackBlocks();
	private final List<MorphFlight> flights = new ArrayList<>();
	/** Each hand's last drawn pose in camera space, and the tick it was drawn on: where a flight leaves from. */
	private final Matrix4f[] handPoses = {new Matrix4f(), new Matrix4f()};
	private final long[] handPoseTicks = {Long.MIN_VALUE, Long.MIN_VALUE};
	private HandFrame lastFrame = new HandFrame();
	private long ticks;

	/**
	 * Launches a flight into a placed block; false when its item cannot become the block (a flat icon,
	 * a special model). {@code swingTicks}: the swing's length the flight is timed by.
	 */
	boolean launch(LocalPlayer player, ClientLevel level, PlacedBlock placed, int swingTicks) {
		HumanoidArm arm = armOf(player, placed.hand());
		ItemStackRenderState model = ModelFaces.heldModel(player, placed.held(), arm);
		BlockMorph.Target target = BlockMorph.resolve(model, placed, level);
		if (target == null) {
			return false;
		}
		flights.add(new MorphFlight(target, model, placed.hand(), launchPose(placed.hand(), arm), ticks, RightClickPace.ticks(swingTicks * SWING_SHARE)));
		heldBack.holdBack(level, placed);
		return true;
	}

	/** Start of every client tick; {@code enabled}: the feature is on (off drops every flight). */
	void tick(boolean enabled) {
		ticks++;
		if (!enabled) {
			flights.clear();
		}
		boolean waitForRecord = VisionHooks.tracksMotion();
		for (MorphFlight flight : flights) {
			flight.tick(ticks, waitForRecord);
		}
		flights.removeIf(MorphFlight::done);
		heldBack.revealAllBut(travellingBlocks());
	}

	/** Right before a held item is drawn: its pose, for flights launched from this hand. */
	void noteHandPose(InteractionHand hand, HandFrame frame, PoseStack pose) {
		lastFrame = frame;
		handPoses[hand.ordinal()].set(BlockMorph.cameraPose(frame, pose.last().pose()));
		handPoseTicks[hand.ordinal()] = ticks;
	}

	/** Draws every flight; call at the end of the hand pass. Flights that left the screen end here. */
	void draw(PoseStack pose, SubmitNodeCollector collector, int light, HandFrame frame, float partialTicks) {
		lastFrame = frame;
		boolean ended = false;
		for (MorphFlight flight : flights) {
			if (!flight.done() && !drawTracked(pose, collector, light, flight, frame, ticks + partialTicks)) {
				flight.end();
				ended = true;
			}
		}
		if (ended) {
			heldBack.revealAllBut(travellingBlocks());
		}
	}

	/**
	 * Draws one flight as an object of its own for the vision sensor's flow: it carries on the held
	 * item it left, and once settled on its block it hands its pixels over to the block.
	 */
	private static boolean drawTracked(PoseStack pose, SubmitNodeCollector collector, int light, MorphFlight flight, HandFrame frame, double now) {
		boolean settled = flight.settled(now);
		boolean recorded = VisionHooks.beginHandObject(flight, flight.hand, settled);
		try {
			boolean drawn = BlockMorph.draw(pose, collector, light, flight, frame, flight.weight(now));
			if (drawn && recorded && settled) {
				flight.noteSettledRecorded();
			}
			return drawn;
		} finally {
			VisionHooks.endHandObject();
		}
	}

	/** What the chunk mesher draws at a position (any thread). */
	BlockState meshed(int x, int y, int z, BlockState real) {
		return heldBack.meshed(x, y, z, real);
	}

	/** Where a flight leaves {@code hand}: its last drawn pose, or the resting place when it held nothing. */
	private Matrix4f launchPose(InteractionHand hand, HumanoidArm arm) {
		int index = hand.ordinal();
		boolean fresh = ticks - handPoseTicks[index] <= HAND_POSE_MAX_AGE;
		return fresh ? new Matrix4f(handPoses[index]) : BlockMorph.restCameraPose(lastFrame, arm);
	}

	/** Blocks a flight is still on its way to, which stay held back. */
	private Set<BlockPos> travellingBlocks() {
		Set<BlockPos> blocks = new HashSet<>();
		for (MorphFlight flight : flights) {
			if (flight.travelling()) {
				blocks.add(flight.target.pos());
			}
		}
		return blocks;
	}

	private static HumanoidArm armOf(LocalPlayer player, InteractionHand hand) {
		return hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
	}
}
