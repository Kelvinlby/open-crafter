package mod.kelvinlby.opencrafter.client.handview;

import mod.kelvinlby.opencrafter.api.ModContext;
import mod.kelvinlby.opencrafter.api.ModModule;
import mod.kelvinlby.opencrafter.client.interaction.Interactions;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** First-person hand view tweaks; purely visual, configured from the settings screen. Animates every interaction, whoever sent it. */
public final class HandViewModule implements ModModule {
	@Override
	public void install(ModContext context) {
		HandView view = new HandView(context.config());
		HandViewHooks.install(view);
		Interactions.subscribe(view.actions);
		ClientTickEvents.START_CLIENT_TICK.register(view::onTickStart);
		ClientTickEvents.END_CLIENT_TICK.register(view::onTickEnd);
	}
}
