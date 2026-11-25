package mod.kelvinlby.opencrafter.client.input;

import net.minecraft.client.Minecraft;

/** Static entry points for the input mixins; no-ops until {@link InputModule} is installed. */
public final class InputHooks {
	private static KeySimulator simulator;

	private InputHooks() {
	}

	static void install(KeySimulator keySimulator) {
		simulator = keySimulator;
	}

	/** Start of every frame, before queued backend ops run: expire leases. */
	public static void onFrame() {
		if (simulator != null) {
			simulator.update();
		}
	}

	/** After {@code KeyMapping.setAll} copied the physical keyboard state into every mapping. */
	public static void afterKeyStatesResynced() {
		if (simulator != null) {
			simulator.update();
		}
	}

	/** True while a simulated attack hold is applied, so mining continues without a grabbed mouse. */
	public static boolean isSimulatingAttack() {
		return simulator != null && simulator.isApplied(Minecraft.getInstance().options.keyAttack);
	}
}
