package mod.kelvinlby.opencrafter.client.vision.record;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import net.minecraft.client.renderer.feature.FeatureRendererType;
import net.minecraft.client.renderer.feature.GizmoFeatureRenderer;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.feature.QuadParticleFeatureRenderer;
import net.minecraft.client.renderer.feature.ShadowFeatureRenderer;
import net.minecraft.client.renderer.feature.ShapeOutlineFeatureRenderer;
import net.minecraft.client.renderer.feature.submit.SubmitNode;
import org.joml.Matrix4f;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Records the view-space vertices of moving objects (entities, block entities, particles and
 * the first-person hand) on captured frames, keyed so the same surface can be found again in
 * the next captured frame.
 *
 * <p>Keys are assigned when a submit node is created (we then know which object is being
 * submitted); vertices are recorded when the feature renderers turn those submits into
 * vertices. Render thread only.
 */
public final class MotionRecorder {
	/** Submits that never are the visible surface of a moving object (decals, outlines, debug). */
	private static final Set<FeatureRendererType<?>> IGNORED_FEATURES = Set.of(
		ShadowFeatureRenderer.TYPE,
		ShapeOutlineFeatureRenderer.TYPE,
		GizmoFeatureRenderer.TYPE,
		QuadParticleFeatureRenderer.TYPE
	);
	private static final String PARTICLE_QUAD = "particle quad";

	private @Nullable FrameGeometry frame;
	private final Deque<RecordPhase> phases = new ArrayDeque<>();
	private final Deque<Object> owners = new ArrayDeque<>();
	private final Map<SubmitKey, Integer> ordinals = new HashMap<>();
	private final Map<Object, SubmitKey> submitKeys = new IdentityHashMap<>();
	private final Map<Object, Integer> entityIds = new IdentityHashMap<>();
	private final Map<Object, Map<Object, List<SubmitKey>>> particleQuads = new IdentityHashMap<>();
	private @Nullable Object currentParticle;
	private @Nullable GeometryRecord preparing;
	private Matrix4f groupModelView = new Matrix4f();

	public void beginFrame(boolean record) {
		frame = record ? new FrameGeometry() : null;
		phases.clear();
		owners.clear();
		ordinals.clear();
		submitKeys.clear();
		entityIds.clear();
		particleQuads.clear();
		currentParticle = null;
		preparing = null;
	}

	/** Ends recording for this frame and returns what was recorded ({@code null} if nothing was armed). */
	public @Nullable FrameGeometry finishFrame() {
		FrameGeometry finished = frame;
		beginFrame(false);
		return finished;
	}

	public boolean isRecording() {
		return frame != null;
	}

	// ---- submission: who owns the submits created right now ----

	public void pushPhase(RecordPhase phase) {
		phases.push(phase);
	}

	public void popPhase() {
		phases.poll();
	}

	public void pushOwner(@Nullable Object owner) {
		owners.push(owner == null ? NO_OWNER : owner);
	}

	public void popOwner() {
		owners.poll();
	}

	/**
	 * Hand-pass geometry that is an object of its own, such as a block flying from the hand into the
	 * world: recorded in the hand phase under {@code owner} (an identity that lasts as long as the
	 * object), wherever it is drawn. {@code continues}: the owner whose geometry it carries on, when
	 * that is gone (a flight leaving an emptied hand). {@code settled}: it sits exactly on world
	 * geometry this frame, which is what shows there once it is gone. Pair with {@link #endHandObject}.
	 */
	public void beginHandObject(@Nullable Object owner, @Nullable Object continues, boolean settled) {
		pushPhase(RecordPhase.HAND);
		pushOwner(owner);
		if (frame == null || owner == null) {
			return;
		}
		if (continues != null) {
			frame.noteSuccessor(continues, owner);
		}
		if (settled) {
			frame.noteSettled(owner);
		}
	}

	public void endHandObject() {
		popOwner();
		popPhase();
	}

	public void onEntityExtracted(Object renderState, int entityId) {
		if (frame != null) {
			entityIds.put(renderState, entityId);
		}
	}

	public @Nullable Object entityOwner(Object renderState) {
		Integer id = entityIds.get(renderState);
		return id == null ? null : new SubmitKey.EntityOwner(id);
	}

	public void onSubmit(SubmitNode node) {
		RecordPhase phase = phases.peek();
		Object owner = owners.peek();
		if (frame == null || phase == null || owner == null || owner == NO_OWNER || IGNORED_FEATURES.contains(node.featureType())) {
			return;
		}
		submitKeys.put(node, nextKey(phase, owner, discriminator(node)));
	}

	private SubmitKey nextKey(RecordPhase phase, Object owner, Object discriminator) {
		SubmitKey base = new SubmitKey(phase, owner, discriminator, 0);
		int ordinal = ordinals.merge(base, 1, Integer::sum) - 1;
		return new SubmitKey(phase, owner, discriminator, ordinal);
	}

	/** Model submits are told apart by their root part: models wrapping a single part are re-created every frame. */
	private static Object discriminator(SubmitNode node) {
		return node instanceof ModelFeatureRenderer.Submit<?> model ? model.model().root() : node.featureType();
	}

	// ---- preparation: submits turning into vertices ----

	public boolean hasTaggedSubmits() {
		return frame != null && !submitKeys.isEmpty();
	}

	public void beginGroup() {
		groupModelView = RenderSystem.getModelViewMatrixCopy();
	}

	public void beginPrepare(Object submit) {
		SubmitKey key = frame == null ? null : submitKeys.get(submit);
		preparing = key == null ? null : frame.recordFor(key);
	}

	public void endPrepare() {
		preparing = null;
	}

	public VertexConsumer wrapSubmitBuilder(VertexConsumer builder, PrimitiveTopology topology) {
		GeometryRecord record = preparing;
		if (record == null) {
			return builder;
		}
		record.beginSegment(topology);
		return new RecordingVertexConsumer(builder, groupModelView, () -> record);
	}

	// ---- particles: one key per emitted quad, owned by the particle instance ----

	public void setCurrentParticle(@Nullable Object particle) {
		currentParticle = particle;
	}

	public void onParticleQuad(Object renderState, Object layer) {
		if (frame == null) {
			return;
		}
		SubmitKey key = currentParticle == null ? null : nextKey(RecordPhase.WORLD, currentParticle, PARTICLE_QUAD);
		particleQuads.computeIfAbsent(renderState, ignored -> new IdentityHashMap<>())
			.computeIfAbsent(layer, ignored -> new ArrayList<>())
			.add(key);
	}

	public VertexConsumer wrapParticleLayer(Object renderState, Object layer, VertexConsumer builder) {
		Map<Object, List<SubmitKey>> layers = particleQuads.get(renderState);
		List<SubmitKey> quads = layers == null ? null : layers.get(layer);
		if (frame == null || quads == null) {
			return builder;
		}
		return new RecordingVertexConsumer(builder, RenderSystem.getModelViewMatrixCopy(), new ParticleQuadSelector(frame, quads));
	}

	private static final Object NO_OWNER = new Object();

	/** Sends every group of four vertices to the record of the particle that emitted that quad. */
	private static final class ParticleQuadSelector implements RecordingVertexConsumer.RecordSelector {
		private final FrameGeometry frame;
		private final List<SubmitKey> quads;
		private int vertex;
		private @Nullable GeometryRecord current;

		private ParticleQuadSelector(FrameGeometry frame, List<SubmitKey> quads) {
			this.frame = frame;
			this.quads = quads;
		}

		@Override
		public @Nullable GeometryRecord nextVertex() {
			if (vertex % 4 == 0) {
				current = startQuad(vertex / 4);
			}
			vertex++;
			return current;
		}

		private @Nullable GeometryRecord startQuad(int quad) {
			SubmitKey key = quad < quads.size() ? quads.get(quad) : null;
			if (key == null) {
				return null;
			}
			GeometryRecord record = frame.recordFor(key);
			record.beginSegment(PrimitiveTopology.QUADS);
			return record;
		}
	}
}
