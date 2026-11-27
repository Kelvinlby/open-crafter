package mod.kelvinlby.opencrafter.client.contact;

import mod.kelvinlby.opencrafter.api.ModContext;
import mod.kelvinlby.opencrafter.api.ModModule;
import mod.kelvinlby.opencrafter.client.contact.crack.CrackOverlay;
import mod.kelvinlby.opencrafter.client.contact.hinge.HiddenBlocks;
import mod.kelvinlby.opencrafter.client.contact.hinge.HingeSwing;
import mod.kelvinlby.opencrafter.client.contact.push.HitPush;
import mod.kelvinlby.opencrafter.client.contact.render.TempBlocks;
import mod.kelvinlby.opencrafter.client.contact.shrink.BreakShrink;
import mod.kelvinlby.opencrafter.client.interaction.Interactions;
import mod.kelvinlby.opencrafter.core.config.ConfigStore;
import mod.kelvinlby.opencrafter.core.config.ContactEffectsConfig;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;

/**
 * Contact effects (docs/ops/contact-effects.md): every confirmed in-world action, whoever sent
 * it, shows its consequence where it happened and over a few frames: cracks while breaking, the
 * broken block shrinking away, hinges swinging, hit entities pushed. Purely visual.
 */
public final class ContactEffectsModule implements ModModule {
	@Override
	public void install(ModContext context) {
		ConfigStore config = context.config();
		EffectClock clock = new EffectClock();
		TempBlocks blocks = new TempBlocks();
		HiddenBlocks hidden = new HiddenBlocks();
		CrackOverlay cracks = new CrackOverlay(config);
		HitPush pushes = new HitPush(config, clock);
		Interactions.subscribe(cracks);
		Interactions.subscribe(new BreakShrink(config, blocks, clock));
		Interactions.subscribe(new HingeSwing(config, blocks, hidden, clock));
		Interactions.subscribe(pushes);
		ContactHooks.install(clock, blocks, hidden, pushes);
		ClientTickEvents.END_CLIENT_TICK.register(client -> tick(config, clock, blocks, cracks, pushes));
		ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> leaveWorld(blocks, hidden, cracks, pushes));
	}

	private static void tick(ConfigStore config, EffectClock clock, TempBlocks blocks, CrackOverlay cracks, HitPush pushes) {
		clock.tick();
		ContactEffectsConfig settings = config.get().contactEffects;
		if (!settings.breakShrink && !settings.hingeSwing) {
			blocks.clear();
		}
		blocks.tick(clock.tickTime());
		cracks.tick();
		pushes.tick();
	}

	private static void leaveWorld(TempBlocks blocks, HiddenBlocks hidden, CrackOverlay cracks, HitPush pushes) {
		blocks.clear();
		hidden.forget();
		cracks.stop();
		pushes.clear();
	}
}
