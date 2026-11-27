package mod.kelvinlby.opencrafter.encoder.client;

import mod.kelvinlby.opencrafter.encoder.EncoderLog;
import net.minecraft.client.Minecraft;
import org.jspecify.annotations.Nullable;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;

/**
 * Whether every loaded chunk section has its mesh, with the vanilla renderer or with Sodium (looked
 * up reflectively; the encoder does not depend on it). Frames rendered while meshes are missing
 * show holes the recorded player never saw, because rendering here runs far ahead of real time.
 */
final class ChunkSettler {
	private final @Nullable MethodHandle sodiumInstance;
	private final @Nullable MethodHandle sodiumComplete;

	ChunkSettler() {
		MethodHandle instance = null;
		MethodHandle complete = null;
		try {
			Class<?> renderer = Class.forName("net.caffeinemc.mods.sodium.client.render.SodiumWorldRenderer");
			MethodHandles.Lookup lookup = MethodHandles.publicLookup();
			instance = lookup.findStatic(renderer, "instanceNullable", MethodType.methodType(renderer));
			complete = lookup.findVirtual(renderer, "isTerrainRenderComplete", MethodType.methodType(boolean.class));
			EncoderLog.LOGGER.info("Chunk settling follows Sodium's renderer");
		} catch (ReflectiveOperationException | LinkageError absent) {
			// Vanilla renderer.
		}
		this.sodiumInstance = instance;
		this.sodiumComplete = complete;
	}

	boolean allSectionsMeshed(Minecraft minecraft) {
		if (minecraft.level == null) {
			return true;
		}
		if (sodiumInstance != null && sodiumComplete != null) {
			return sodiumComplete(minecraft);
		}
		return minecraft.levelRenderer.hasRenderedAllSections();
	}

	private boolean sodiumComplete(Minecraft minecraft) {
		try {
			Object renderer = sodiumInstance.invoke();
			return renderer == null ? minecraft.levelRenderer.hasRenderedAllSections() : (boolean) sodiumComplete.invoke(renderer);
		} catch (Throwable t) {
			EncoderLog.warnThrottled("sodium-settle", "Could not ask Sodium whether terrain is complete", t);
			return true;
		}
	}
}
