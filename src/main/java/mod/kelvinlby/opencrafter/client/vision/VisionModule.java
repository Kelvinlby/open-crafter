package mod.kelvinlby.opencrafter.client.vision;

import mod.kelvinlby.opencrafter.api.ModContext;
import mod.kelvinlby.opencrafter.api.ModModule;
import mod.kelvinlby.opencrafter.api.frame.FramePublisher;
import mod.kelvinlby.opencrafter.client.vision.capture.VisionCapture;
import mod.kelvinlby.opencrafter.client.vision.debug.DebugDumpPublisher;
import mod.kelvinlby.opencrafter.client.vision.debug.DebugScenario;
import mod.kelvinlby.opencrafter.client.vision.flow.FlowRenderer;
import mod.kelvinlby.opencrafter.client.vision.gpu.VisionPipelines;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

import java.util.Arrays;

/** Vision sensor: RGB and ground-truth optical flow of the final presented frame. */
public final class VisionModule implements ModModule {
	private VisionControl control;

	@Override
	public void install(ModContext context) {
		VisionPipelines.bootstrap();
		VisionSettings settings = new VisionSettings(context.config());
		VisionStats stats = new VisionStats();
		FramePublisher publisher = DebugDumpPublisher.wrapIfRequested(context.frames());
		FlowRenderer flow = new FlowRenderer(VisionHooks.recorder());
		VisionCapture capture = new VisionCapture(settings, publisher, stats, flow, context.annotators());
		VisionHooks.install(capture, flow);
		ClientTickEvents.END_CLIENT_TICK.register(client -> capture.onClientTick());
		context.config().onReplace((old, edited) -> settings.useRate(edited.vision.rateMode, edited.vision.fixedHz));
		control = new VisionControl(settings, stats, publisher);
		applyStartupStreams(control);
		new VisionOps(control).register(context.handlers());
		DebugScenario.installIfRequested();
	}

	/** The control surface, for the settings screen; available after {@link #install}. */
	public VisionControl control() {
		return control;
	}

	/** {@code -Dopencrafter.vision.streams=rgb,flow,...} sets the streams before any backend connects (testing aid). */
	private static void applyStartupStreams(VisionControl control) {
		String streams = System.getProperty("opencrafter.vision.streams");
		if (streams != null) {
			control.setStreams(Arrays.stream(streams.split(",")).map(String::strip).map(VisionStream::byWireName).toList());
		}
	}
}
