package mod.kelvinlby.opencrafter.client.vision.gui;

import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.textures.GpuSampler;
import com.mojang.renderpearl.api.textures.GpuTextureView;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import org.jspecify.annotations.Nullable;

/**
 * One GUI element (a blit, glyph, item icon, filled rectangle...) as it was built in one frame:
 * its vertices in framebuffer pixels (y down), texture coordinates and alpha, plus what is needed
 * to recognise it in another frame.
 */
public final class GuiElement {
	final PrimitiveTopology topology;
	final @Nullable GpuTextureView texture;
	final @Nullable GpuSampler sampler;
	/** Scissor rectangle in pixels (left, top, right, bottom); the whole screen when unclipped. */
	final float[] clip;
	final FloatArrayList positions = new FloatArrayList(8);
	final FloatArrayList uvs = new FloatArrayList(8);
	final FloatArrayList alphas = new FloatArrayList(4);
	/** Position in the frame's draw order (later = on top). */
	final int order;
	private final Object pipeline;
	/** For item icons: the item's model identity (atlas regions get re-packed as items come and go). */
	private final long itemIdentity;
	private long key;

	GuiElement(int order, Object pipeline, PrimitiveTopology topology, @Nullable GpuTextureView texture, @Nullable GpuSampler sampler, float[] clip) {
		this(order, pipeline, topology, texture, sampler, clip, 0L);
	}

	GuiElement(int order, Object pipeline, PrimitiveTopology topology, @Nullable GpuTextureView texture, @Nullable GpuSampler sampler,
		float[] clip, long itemIdentity) {
		this.order = order;
		this.itemIdentity = itemIdentity;
		this.pipeline = pipeline;
		this.topology = topology;
		this.texture = texture;
		this.sampler = sampler;
		this.clip = clip;
	}

	void addVertex(float x, float y) {
		positions.add(x);
		positions.add(y);
		uvs.add(0.0F);
		uvs.add(0.0F);
		alphas.add(1.0F);
	}

	void setUv(float u, float v) {
		int last = uvs.size() - 2;
		if (last >= 0) {
			uvs.set(last, u);
			uvs.set(last + 1, v);
		}
	}

	void setAlpha(float alpha) {
		int last = alphas.size() - 1;
		if (last >= 0) {
			alphas.set(last, alpha);
		}
	}

	int vertexCount() {
		return alphas.size();
	}

	boolean textured() {
		return texture != null;
	}

	/** Same pipeline, texture, texture region (or item) and vertex count = the same thing on screen. */
	long key() {
		if (key == 0) {
			key = computeKey();
		}
		return key;
	}

	float centroidX() {
		return centroid(0);
	}

	float centroidY() {
		return centroid(1);
	}

	/** True when every vertex sits where {@code other}'s does (within 1/100 pixel). */
	boolean samePlaceAs(GuiElement other) {
		for (int i = 0; i < positions.size(); i++) {
			if (Math.abs(positions.getFloat(i) - other.positions.getFloat(i)) > 0.01F) {
				return false;
			}
		}
		return true;
	}

	private float centroid(int axis) {
		float sum = 0.0F;
		for (int i = axis; i < positions.size(); i += 2) {
			sum += positions.getFloat(i);
		}
		return sum / Math.max(1, vertexCount());
	}

	private long computeKey() {
		long hash = System.identityHashCode(pipeline);
		hash = hash * 31 + vertexCount();
		if (itemIdentity != 0) {
			return nonZero(hash * 31 + itemIdentity);
		}
		hash = hash * 31 + System.identityHashCode(texture);
		for (int i = 0; i < uvs.size(); i++) {
			hash = hash * 31 + Math.round(uvs.getFloat(i) * 65536.0F);
		}
		return nonZero(hash);
	}

	private static long nonZero(long hash) {
		return hash == 0 ? 1 : hash;
	}
}
