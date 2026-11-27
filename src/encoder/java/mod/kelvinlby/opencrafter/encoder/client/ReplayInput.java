package mod.kelvinlby.opencrafter.encoder.client;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;

/** The local player's input, set from the recorded {@code PlayerInput} packets instead of the keyboard. */
final class ReplayInput extends ClientInput {
	void set(Input input) {
		keyPresses = input;
		moveVector = new Vec2(impulse(input.left(), input.right()), impulse(input.forward(), input.backward())).normalized();
	}

	private static float impulse(boolean positive, boolean negative) {
		return positive == negative ? 0.0F : positive ? 1.0F : -1.0F;
	}
}
