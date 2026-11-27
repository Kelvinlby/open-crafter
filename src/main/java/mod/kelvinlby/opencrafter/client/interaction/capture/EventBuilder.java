package mod.kelvinlby.opencrafter.client.interaction.capture;

import mod.kelvinlby.opencrafter.client.interaction.event.Aim;
import mod.kelvinlby.opencrafter.client.interaction.event.InteractionEvent;
import mod.kelvinlby.opencrafter.client.interaction.event.InteractionTarget;
import mod.kelvinlby.opencrafter.client.interaction.outcome.PreAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Util;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/** Turns what a packet says, plus the client's context, into an {@link InteractionEvent}. */
final class EventBuilder {
	private EventBuilder() {
	}

	static InteractionEvent build(long id, long frameBefore, SentAction action, PreAction before, Aim sentAim,
		boolean fromGameMode, Minecraft minecraft, LocalPlayer player, ClientLevel level) {
		Aim aim = action.aim() != null ? action.aim() : sentAim;
		ItemStack held = action.hand() == null ? ItemStack.EMPTY : before.held(action.hand());
		return new InteractionEvent(
			id,
			Util.getNanos(),
			frameBefore,
			action.kind(),
			action.hand(),
			held,
			action.target(),
			contact(action, aim, minecraft, player, level),
			aim,
			Crosshair.aligned(minecraft, player, action.target(), aim),
			fromGameMode
		);
	}

	private static @Nullable Vec3 contact(SentAction action, Aim aim, Minecraft minecraft, LocalPlayer player, ClientLevel level) {
		return switch (action.kind()) {
			case USE_ON_BLOCK -> action.location();
			case BREAK_START, BREAK_DONE, BREAK_ABORT -> blockContact(minecraft, (InteractionTarget.Block) action.target());
			case ATTACK_ENTITY -> attackContact(minecraft, player, level, (InteractionTarget.Entity) action.target(), aim);
			case INTERACT_ENTITY -> interactContact(level, (InteractionTarget.Entity) action.target(), action.location());
			case STAB -> ContactPoints.alongRay(player.getEyePosition(), aim, player.entityInteractionRange());
			case USE_ITEM, RELEASE_USE -> null;
		};
	}

	/** The crosshair's exact hit on the block when it points there, else the centre of the face. */
	private static Vec3 blockContact(Minecraft minecraft, InteractionTarget.Block target) {
		BlockHitResult hit = Crosshair.blockHit(minecraft, target);
		return hit != null ? hit.getLocation() : ContactPoints.faceCentre(target.pos(), target.face());
	}

	/** The crosshair's exact hit on the entity when it points there, else where the aim meets its box. */
	private static @Nullable Vec3 attackContact(Minecraft minecraft, LocalPlayer player, ClientLevel level, InteractionTarget.Entity target, Aim aim) {
		EntityHitResult hit = Crosshair.entityHit(minecraft, target.entityId());
		if (hit != null) {
			return hit.getLocation();
		}
		Entity entity = level.getEntity(target.entityId());
		return entity == null ? null : ContactPoints.onBox(entity.getBoundingBox(), player.getEyePosition(), aim);
	}

	private static @Nullable Vec3 interactContact(ClientLevel level, InteractionTarget.Entity target, @Nullable Vec3 relative) {
		Entity entity = level.getEntity(target.entityId());
		return entity == null || relative == null ? null : ContactPoints.entityRelative(entity, relative);
	}
}
