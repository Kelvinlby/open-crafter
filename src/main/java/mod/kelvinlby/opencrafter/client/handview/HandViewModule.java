package mod.kelvinlby.opencrafter.client.handview;

import mod.kelvinlby.opencrafter.api.ModContext;
import mod.kelvinlby.opencrafter.api.ModModule;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

/** First-person hand view tweaks; purely visual, configured from the settings screen. */
public final class HandViewModule implements ModModule {
	@Override
	public void install(ModContext context) {
		HandView view = new HandView(context.config());
		HandViewHooks.install(view);
		ClientTickEvents.START_CLIENT_TICK.register(view::onTickStart);
		ClientTickEvents.END_CLIENT_TICK.register(view::onTickEnd);
	}
}
