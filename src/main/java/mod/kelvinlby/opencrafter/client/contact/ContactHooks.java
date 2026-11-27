package mod.kelvinlby.opencrafter.client.contact;

import mod.kelvinlby.opencrafter.client.contact.hinge.HiddenBlocks;
import mod.kelvinlby.opencrafter.client.contact.push.HitPush;
import mod.kelvinlby.opencrafter.client.contact.render.TempBlocks;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Static entry points for the contact mixins; each is a cheap no-op while no effect runs. */
public final class ContactHooks {
	private static @Nullable Installed installed;

	private ContactHooks() {
	}

	static void install(EffectClock clock, TempBlocks blocks, HiddenBlocks hidden, HitPush pushes) {
		installed = new Installed(clock, blocks, hidden, pushes);
	}

	/** Inside the level's feature submission: draws the temporary block models. */
	public static void submitWorld(SubmitNodeCollector collector, Vec3 camera) {
		Installed current = installed;
		if (current != null && !current.blocks.isEmpty()) {
			current.blocks.submit(collector, camera, current.clock.frameTime());
		}
	}

	/** What the chunk mesher draws at a position (any thread). */
	public static BlockState meshedState(int x, int y, int z, BlockState state) {
		Installed current = installed;
		return current == null ? state : current.hidden.meshed(x, y, z, state);
	}

	/** Right after an entity's render state was extracted: a hit entity is drawn pushed. */
	public static void onEntityExtracted(EntityRenderState state, int entityId) {
		Installed current = installed;
		if (current != null) {
			current.pushes.displace(state, entityId);
		}
	}

	private record Installed(EffectClock clock, TempBlocks blocks, HiddenBlocks hidden, HitPush pushes) {
	}
}
