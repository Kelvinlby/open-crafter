package mod.kelvinlby.opencrafter.client.contact.push;

import mod.kelvinlby.opencrafter.client.interaction.event.InteractionEvent;
import mod.kelvinlby.opencrafter.client.interaction.event.InteractionTarget;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.Comparator;

/** Which entity a confirmed hit hurt, and where it was hit. */
final class HitTargets {
	/** Same slack the interaction layer allows a stab ray. */
	private static final double STAB_SLACK = 0.3;

	private HitTargets() {
	}

	/** The attacked entity, or for a stab the hurt living entity nearest along the stab. */
	static @Nullable Entity hurt(InteractionEvent event, ClientLevel level, LocalPlayer player) {
		if (event.target() instanceof InteractionTarget.Entity target) {
			return level.getEntity(target.entityId());
		}
		Vec3 end = event.contact();
		return end == null ? null : stabbed(level, player, player.getEyePosition(), end);
	}

	/** Where the hit met the entity: the event's contact point, else the entity's centre seen from the player. */
	static Vec3 contact(InteractionEvent event, Entity entity, LocalPlayer player) {
		if (event.contact() != null && event.target() instanceof InteractionTarget.Entity) {
			return event.contact();
		}
		return entity.getBoundingBox().clip(player.getEyePosition(), entity.getBoundingBox().getCenter()).orElse(player.getEyePosition());
	}

	private static @Nullable Entity stabbed(ClientLevel level, LocalPlayer player, Vec3 eye, Vec3 end) {
		AABB reach = new AABB(eye, end).inflate(STAB_SLACK);
		return level.getEntities(player, reach, entity -> entity instanceof LivingEntity living && living.hurtTime > 0 && crosses(entity, eye, end))
			.stream()
			.min(Comparator.comparingDouble(entity -> entity.distanceToSqr(eye)))
			.orElse(null);
	}

	private static boolean crosses(Entity entity, Vec3 from, Vec3 to) {
		return entity.getBoundingBox().inflate(STAB_SLACK).clip(from, to).isPresent();
	}
}
