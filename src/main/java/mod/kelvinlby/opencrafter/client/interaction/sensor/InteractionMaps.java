package mod.kelvinlby.opencrafter.client.interaction.sensor;

import mod.kelvinlby.opencrafter.client.interaction.event.BlockChange;
import mod.kelvinlby.opencrafter.client.interaction.event.InteractionEvent;
import mod.kelvinlby.opencrafter.client.interaction.event.InteractionTarget;
import mod.kelvinlby.opencrafter.client.interaction.event.Outcome;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.util.Util;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/** Wire form of interactions and outcomes (see docs/ops/interaction.md). */
final class InteractionMaps {
	private InteractionMaps() {
	}

	static Map<String, Object> event(InteractionEvent event) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("id", event.id());
		map.put("nanos", event.nanos());
		map.put("frame_before", event.frameBefore());
		map.put("kind", event.kind().wireName());
		map.put("hand", hand(event.hand()));
		map.put("item", item(event.held()));
		map.put("target", target(event.target()));
		map.put("contact", vec(event.contact()));
		map.put("aim", Map.of("yaw", (double) event.aim().yaw(), "pitch", (double) event.aim().pitch()));
		map.put("crosshair_aligned", event.crosshairAligned());
		map.put("from_game_mode", event.fromGameMode());
		return map;
	}

	static Map<String, Object> outcome(InteractionEvent event, Outcome outcome) {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("id", event.id());
		map.put("nanos", Util.getNanos());
		map.put("effect", outcome.effect());
		map.put("reason", outcome.reason());
		map.put("block_changes", outcome.blockChanges().stream().map(InteractionMaps::blockChange).toList());
		return map;
	}

	private static Map<String, Object> target(InteractionTarget target) {
		return switch (target) {
			case InteractionTarget.None ignored -> Map.of("type", "none");
			case InteractionTarget.Entity entity -> Map.of("type", "entity", "entity_id", entity.entityId());
			case InteractionTarget.Block block -> Map.of("type", "block", "pos", pos(block.pos()), "face", block.face().getSerializedName());
		};
	}

	private static Map<String, Object> blockChange(BlockChange change) {
		return Map.of("pos", pos(change.pos()), "before", state(change.before()), "after", state(change.after()));
	}

	private static Map<String, Object> pos(BlockPos pos) {
		return Map.of("x", pos.getX(), "y", pos.getY(), "z", pos.getZ());
	}

	private static String state(BlockState state) {
		return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
	}

	private static @Nullable String item(ItemStack stack) {
		return stack.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(stack.getItem()).toString();
	}

	private static @Nullable String hand(@Nullable InteractionHand hand) {
		if (hand == null) {
			return null;
		}
		return hand == InteractionHand.MAIN_HAND ? "main" : "off";
	}

	static @Nullable Map<String, Object> vec(@Nullable Vec3 vec) {
		if (vec == null) {
			return null;
		}
		Map<String, Object> map = new HashMap<>();
		map.put("x", vec.x);
		map.put("y", vec.y);
		map.put("z", vec.z);
		return map;
	}
}
