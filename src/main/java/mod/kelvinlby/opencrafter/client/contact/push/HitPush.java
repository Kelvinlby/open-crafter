package mod.kelvinlby.opencrafter.client.contact.push;

import mod.kelvinlby.opencrafter.client.contact.EffectClock;
import mod.kelvinlby.opencrafter.client.interaction.event.InteractionEvent;
import mod.kelvinlby.opencrafter.client.interaction.event.InteractionKind;
import mod.kelvinlby.opencrafter.client.interaction.event.InteractionListener;
import mod.kelvinlby.opencrafter.client.interaction.event.Outcome;
import mod.kelvinlby.opencrafter.core.config.ConfigStore;
import mod.kelvinlby.opencrafter.core.config.ContactEffectsConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

import java.util.HashMap;
import java.util.Map;

/**
 * A confirmed hit on an entity: its whole model is pushed a few centimetres away from the
 * contact point and eases back. Only where it is drawn moves (its render state), never the entity.
 */
public final class HitPush implements InteractionListener {
	private static final double FLAT = 1.0e-4;

	private final ConfigStore config;
	private final EffectClock clock;
	/** Entity id → its running push. */
	private final Map<Integer, Push> pushes = new HashMap<>();

	public HitPush(ConfigStore config, EffectClock clock) {
		this.config = config;
		this.clock = clock;
	}

	@Override
	public void onOutcome(InteractionEvent event, Outcome outcome) {
		ContactEffectsConfig settings = config.get().contactEffects;
		Minecraft minecraft = Minecraft.getInstance();
		if (!settings.hitPush || !isHurtingHit(event, outcome) || minecraft.level == null || minecraft.player == null) {
			return;
		}
		start(event, settings, minecraft.level, minecraft.player);
	}

	/** Once per tick. */
	public void tick() {
		double now = clock.tickTime();
		if (!config.get().contactEffects.hitPush) {
			pushes.clear();
		}
		pushes.values().removeIf(push -> push.finished(now));
	}

	public void clear() {
		pushes.clear();
	}

	/** Right after an entity's render state was extracted for this frame. */
	public void displace(EntityRenderState state, int entityId) {
		Push push = pushes.isEmpty() ? null : pushes.get(entityId);
		if (push != null) {
			Vec3 offset = push.offset(clock.frameTime());
			state.x += offset.x;
			state.y += offset.y;
			state.z += offset.z;
		}
	}

	private void start(InteractionEvent event, ContactEffectsConfig settings, ClientLevel level, LocalPlayer player) {
		Entity entity = HitTargets.hurt(event, level, player);
		if (entity == null) {
			return;
		}
		Vec3 away = awayFrom(HitTargets.contact(event, entity, player), entity, player);
		double distance = Math.clamp(settings.hitPushBlocks, 0.0, ContactEffectsConfig.MAX_PUSH_BLOCKS);
		int ticks = Math.clamp(settings.hitPushTicks, ContactEffectsConfig.MIN_TICKS, ContactEffectsConfig.MAX_TICKS);
		pushes.put(entity.getId(), new Push(away.scale(distance), ticks, clock.tickTime()));
	}

	/** Horizontal direction from the contact point through the entity's centre (from the player if they coincide). */
	private static Vec3 awayFrom(Vec3 contact, Entity entity, LocalPlayer player) {
		Vec3 away = flat(entity.getBoundingBox().getCenter().subtract(contact));
		if (away.lengthSqr() < FLAT) {
			away = flat(entity.position().subtract(player.position()));
		}
		return away.lengthSqr() < FLAT ? Vec3.ZERO : away.normalize();
	}

	private static Vec3 flat(Vec3 vector) {
		return new Vec3(vector.x, 0.0, vector.z);
	}

	private static boolean isHurtingHit(InteractionEvent event, Outcome outcome) {
		boolean hit = event.kind() == InteractionKind.ATTACK_ENTITY || event.kind() == InteractionKind.STAB;
		return hit && outcome.effect() && "entity_hurt".equals(outcome.reason());
	}
}
