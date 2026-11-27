package mod.kelvinlby.opencrafter.client.vision.capture;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-frame metadata published next to the streams. Captured on the render thread at the
 * moment the frame is captured, so it describes exactly the pixels being read back.
 *
 * @param shaderPackInUse    an Iris shader pack rendered the world (flow is best effort, see docs/ops/vision.md)
 * @param flowFromFrameIndex index of the frame the flow streams refer to (-1 when none)
 * @param annotations        entries other modules add through {@code FrameAnnotators}
 */
public record FrameMeta(
	long frameIndex,
	long captureNanos,
	long clientTick,
	long gameTime,
	float partialTick,
	boolean levelRendered,
	double cameraX,
	double cameraY,
	double cameraZ,
	float cameraYaw,
	float cameraPitch,
	int width,
	int height,
	String backend,
	boolean shaderPackInUse,
	long flowFromFrameIndex,
	Map<String, Object> annotations
) {
	public Map<String, Object> toMap() {
		Map<String, Object> map = new LinkedHashMap<>();
		map.put("frame_index", frameIndex);
		map.put("capture_nanos", captureNanos);
		map.put("client_tick", clientTick);
		map.put("game_time", gameTime);
		map.put("partial_tick", (double) partialTick);
		map.put("level_rendered", levelRendered);
		map.put("camera", Map.of(
			"x", cameraX,
			"y", cameraY,
			"z", cameraZ,
			"yaw", (double) cameraYaw,
			"pitch", (double) cameraPitch
		));
		map.put("width", width);
		map.put("height", height);
		map.put("backend", backend);
		map.put("shader_pack_in_use", shaderPackInUse);
		map.put("flow_from_frame_index", flowFromFrameIndex);
		map.putAll(annotations);
		return map;
	}
}
