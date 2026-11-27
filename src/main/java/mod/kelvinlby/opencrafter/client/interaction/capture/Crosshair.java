package mod.kelvinlby.opencrafter.client.interaction.capture;

import mod.kelvinlby.opencrafter.client.interaction.event.Aim;
import mod.kelvinlby.opencrafter.client.interaction.event.InteractionTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import org.jspecify.annotations.Nullable;

/** Whether an action targets what the rendered crosshair points at. */
final class Crosshair {
	private static final float ALIGNED_DEGREES = 1.0F;

	private Crosshair() {
	}

	static boolean aligned(Minecraft minecraft, Player player, InteractionTarget target, Aim aim) {
		return switch (target) {
			case InteractionTarget.Entity entity -> pointsAtEntity(minecraft.hitResult, entity.entityId());
			case InteractionTarget.Block block -> pointsAtBlock(minecraft.hitResult, block);
			case InteractionTarget.None ignored -> aim.degreesFrom(new Aim(player.getYRot(), player.getXRot())) < ALIGNED_DEGREES;
		};
	}

	/** The block the crosshair points at, when it matches this target (so its exact hit point can be used). */
	static @Nullable BlockHitResult blockHit(Minecraft minecraft, InteractionTarget.Block target) {
		return pointsAtBlock(minecraft.hitResult, target) ? (BlockHitResult) minecraft.hitResult : null;
	}

	/** The entity hit the crosshair points at, when it is this entity. */
	static @Nullable EntityHitResult entityHit(Minecraft minecraft, int entityId) {
		return pointsAtEntity(minecraft.hitResult, entityId) ? (EntityHitResult) minecraft.hitResult : null;
	}

	private static boolean pointsAtEntity(@Nullable HitResult hit, int entityId) {
		return hit instanceof EntityHitResult entityHit && entityHit.getType() == HitResult.Type.ENTITY && entityHit.getEntity().getId() == entityId;
	}

	private static boolean pointsAtBlock(@Nullable HitResult hit, InteractionTarget.Block target) {
		return hit instanceof BlockHitResult blockHit && blockHit.getType() == HitResult.Type.BLOCK && blockHit.getBlockPos().equals(target.pos());
	}
}
