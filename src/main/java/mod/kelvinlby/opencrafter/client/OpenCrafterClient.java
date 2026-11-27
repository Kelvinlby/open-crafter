package mod.kelvinlby.opencrafter.client;

import mod.kelvinlby.opencrafter.OpenCrafter;
import mod.kelvinlby.opencrafter.api.BackendLifecycle;
import mod.kelvinlby.opencrafter.api.ModContext;
import mod.kelvinlby.opencrafter.api.ModModule;
import mod.kelvinlby.opencrafter.api.frame.FrameAnnotators;
import mod.kelvinlby.opencrafter.api.handler.HandlerRegistry;
import mod.kelvinlby.opencrafter.api.sensor.SensorRegistry;
import mod.kelvinlby.opencrafter.client.config.SettingsCommand;
import mod.kelvinlby.opencrafter.client.config.SettingsScreen;
import mod.kelvinlby.opencrafter.client.handview.HandViewModule;
import mod.kelvinlby.opencrafter.client.input.InputModule;
import mod.kelvinlby.opencrafter.client.interaction.InteractionModule;
import mod.kelvinlby.opencrafter.client.inventory.InventoryModule;
import mod.kelvinlby.opencrafter.client.inventoryview.InventoryViewModule;
import mod.kelvinlby.opencrafter.client.status.StatusModule;
import mod.kelvinlby.opencrafter.client.vision.VisionModule;
import mod.kelvinlby.opencrafter.core.config.ConfigStore;
import mod.kelvinlby.opencrafter.core.config.OpenCrafterConfig;
import mod.kelvinlby.opencrafter.transport.TransportModule;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/** Client entrypoint: wires the transport to every feature module and the settings screen. */
public final class OpenCrafterClient implements ClientModInitializer {
	private static final VisionModule VISION = new VisionModule();
	private static final List<ModModule> MODULES = List.of(
		new CoreOpsModule(),
		new StatusModule(),
		new InteractionModule(),
		VISION,
		new InputModule(),
		new InventoryModule(),
		new HandViewModule(),
		new InventoryViewModule()
	);

	@Override
	public void onInitializeClient() {
		ConfigStore config = ConfigStore.load(FabricLoader.getInstance().getConfigDir().resolve(OpenCrafter.MOD_ID + ".json"));
		HandlerRegistry handlers = new HandlerRegistry();
		Executor mainThread = task -> Minecraft.getInstance().execute(task);
		BackendLifecycle lifecycle = new BackendLifecycle();
		TransportModule transport = TransportModule.start(config.get().transport.copy(), handlers, lifecycle, mainThread);
		ModContext context = new ModContext(handlers, new SensorRegistry(), transport.events(), lifecycle, transport.frames(),
			new FrameAnnotators(), config, mainThread);
		MODULES.forEach(module -> module.install(context));
		config.onReplace((old, edited) -> reopenTransportIfChanged(transport, old, edited));
		SettingsScreen.install(config, transport, VISION.control());
		SettingsCommand.register();
		ClientLifecycleEvents.CLIENT_STOPPING.register(client -> transport.close());
		OpenCrafter.LOGGER.info("open-crafter ready with {} ops", handlers.all().size());
	}

	/** Reopens the transport off the main thread (it binds sockets and maps files); backends reconnect. */
	private static void reopenTransportIfChanged(TransportModule transport, OpenCrafterConfig old, OpenCrafterConfig edited) {
		if (old.transport.sameAs(edited.transport)) {
			return;
		}
		CompletableFuture.runAsync(() -> transport.reconfigure(edited.transport.copy()))
			.exceptionally(error -> {
				OpenCrafter.LOGGER.error("Could not apply transport settings", error);
				return null;
			});
	}
}
