package mod.kelvinlby.opencrafter.client.vision;

import mod.kelvinlby.opencrafter.api.ModContext;
import mod.kelvinlby.opencrafter.api.ModModule;
import mod.kelvinlby.opencrafter.api.frame.FramePublisher;
import mod.kelvinlby.opencrafter.client.command.VisionCommand;
import mod.kelvinlby.opencrafter.client.vision.capture.FlowStage;
import mod.kelvinlby.opencrafter.client.vision.capture.VisionCapture;
import mod.kelvinlby.opencrafter.client.vision.debug.DebugDumpPublisher;
import mod.kelvinlby.opencrafter.client.vision.debug.DebugScenario;
import mod.kelvinlby.opencrafter.client.vision.gpu.VisionPipelines;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** Vision sensor: RGB and ground-truth optical flow of the final presented frame. */
public final class VisionModule implements ModModule {
	@Override
	public void install(ModContext context) {
		VisionPipelines.bootstrap();
		VisionSettings settings = new VisionSettings(context.config());
		VisionStats stats = new VisionStats();
		FramePublisher publisher = DebugDumpPublisher.wrapIfRequested(context.frames());
		FlowStage flow = FlowStage.UNSUPPORTED;
		VisionCapture capture = new VisionCapture(settings, publisher, stats, flow);
		VisionHooks.install(capture);
		ClientTickEvents.END_CLIENT_TICK.register(client -> capture.onClientTick());
		VisionControl control = new VisionControl(settings, stats, publisher, flow != FlowStage.UNSUPPORTED);
		new VisionOps(control).register(context.handlers());
		VisionCommand.register(control);
		DebugScenario.installIfRequested();
	}
}
