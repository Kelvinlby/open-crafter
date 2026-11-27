package mod.kelvinlby.opencrafter.client.contact.shrink;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;

/**
 * Where a broken block shrinks to: the item it dropped, followed as it moves, else the block's
 * centre (nothing dropped, or the drop has not arrived yet). Block-local coordinates.
 */
final class DropTarget {
	/** Drops pop up to a quarter block from the centre and move a little before we see them. */
	private static final double SEARCH_MARGIN = 0.5;
	/** Client ticks an item may have existed and still count as this break's drop. */
	private static final int MAX_DROP_AGE = 10;
	/** From the item entity's feet to the middle of its drawn (bobbing) model. */
	private static final double MODEL_LIFT = 0.25;
	private static final Vector3f CENTRE = new Vector3f(0.5F, 0.5F, 0.5F);

	private final BlockPos pos;
	private @Nullable ItemEntity drop;
	private @Nullable Vector3f lastSeen;

	DropTarget(BlockPos pos) {
		this.pos = pos;
	}

	/** The point to shrink towards in the frame {@code partialTick} into the current tick. */
	Vector3f local(float partialTick) {
		if (drop == null) {
			drop = find(pos);
		}
		if (drop != null && !drop.isRemoved()) {
			lastSeen = toLocal(drop.getPosition(partialTick).add(0.0, MODEL_LIFT, 0.0));
		}
		return lastSeen != null ? lastSeen : CENTRE;
	}

	private Vector3f toLocal(Vec3 world) {
		return new Vector3f((float) (world.x - pos.getX()), (float) (world.y - pos.getY()), (float) (world.z - pos.getZ()));
	}

	/** The freshly spawned item nearest the block's centre, if one is there yet. */
	private static @Nullable ItemEntity find(BlockPos pos) {
		ClientLevel level = Minecraft.getInstance().level;
		if (level == null) {
			return null;
		}
		Vec3 centre = Vec3.atCenterOf(pos);
		return level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(SEARCH_MARGIN), DropTarget::fresh)
			.stream()
			.min(Comparator.comparingDouble(item -> item.distanceToSqr(centre)))
			.orElse(null);
	}

	private static boolean fresh(ItemEntity item) {
		return !item.isRemoved() && item.tickCount <= MAX_DROP_AGE;
	}
}
