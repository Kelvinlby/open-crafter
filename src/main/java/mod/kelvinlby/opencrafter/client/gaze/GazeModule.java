package mod.kelvinlby.opencrafter.client.gaze;

import mod.kelvinlby.opencrafter.api.ModContext;
import mod.kelvinlby.opencrafter.api.ModModule;
import mod.kelvinlby.opencrafter.client.gaze.anim.HeldTarget;
import mod.kelvinlby.opencrafter.client.interaction.Interactions;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/**
 * Gaze: the rendered first-person camera looks where the body acts, following a rotation another
 * mod sent to the server only, or turning toward actions off the crosshair (docs/ops/gaze.md).
 * Render-only and off by default.
 */
public final class GazeModule implements ModModule {
	@Override
	public void install(ModContext context) {
		HeldTarget<GazeTarget> targets = new HeldTarget<>();
		GazeDirector director = new GazeDirector(context.config(), targets);
		ServerAimSampler sampler = new ServerAimSampler(targets);
		Interactions.subscribe(new ActionGaze(targets));
		ClientTickEvents.END_CLIENT_TICK.register(sampler::tick);
		ClientPlayConnectionEvents.JOIN.register((handler, sender, client) -> director.reset());
		GazeHooks.install(director);
	}
}
