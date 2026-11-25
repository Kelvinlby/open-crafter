package mod.kelvinlby.opencrafter.client.input.keys;

import com.mojang.blaze3d.platform.InputConstants;
import mod.kelvinlby.opencrafter.client.input.hold.KeyStateMachine;
import mod.kelvinlby.opencrafter.client.input.raw.RawKeyboard;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;

/** {@link KeyStateMachine.Port} backed by the real {@link KeyMapping}s. Main thread only. */
public final class KeyMappingPort implements KeyStateMachine.Port {
	@Override
	public boolean isDown(String key) {
		return test(key, KeyMapping::isDown);
	}

	@Override
	public void setDown(String key, boolean down) {
		apply(key, mapping -> mapping.setDown(down));
	}

	@Override
	public void click(String key) {
		apply(key, KeyMappings::click);
	}

	@Override
	public boolean isToggleMode(String key) {
		return test(key, KeyMappings::isToggleMode);
	}

	@Override
	public boolean isPhysicallyHeld(String key) {
		return test(key, mapping -> !mapping.isUnbound() && isPhysicallyDown(KeyMappings.boundKey(mapping)));
	}

	@Override
	public void deliverToScreen(String key) {
		apply(key, mapping -> {
			if (KeyMappings.isKeyboardBound(mapping)) {
				RawKeyboard.press(KeyMappings.boundKey(mapping).getValue(), 0);
			}
		});
	}

	private static boolean isPhysicallyDown(InputConstants.Key key) {
		return key.getType() == InputConstants.Type.KEYBOARD
			? InputConstants.isKeyDown(key.getValue())
			: isMouseButtonDown(key.getValue());
	}

	private static boolean isMouseButtonDown(int button) {
		MouseHandler mouse = Minecraft.getInstance().mouseHandler;
		return switch (button) {
			case InputConstants.MOUSE_BUTTON_LEFT -> mouse.isLeftPressed();
			case InputConstants.MOUSE_BUTTON_MIDDLE -> mouse.isMiddlePressed();
			case InputConstants.MOUSE_BUTTON_RIGHT -> mouse.isRightPressed();
			default -> false;
		};
	}

	private static boolean test(String key, Predicate<KeyMapping> predicate) {
		return lookup(key).map(predicate::test).orElse(false);
	}

	private static void apply(String key, Consumer<KeyMapping> action) {
		lookup(key).ifPresent(action);
	}

	private static Optional<KeyMapping> lookup(String key) {
		return Optional.ofNullable(KeyMapping.get(key));
	}
}
