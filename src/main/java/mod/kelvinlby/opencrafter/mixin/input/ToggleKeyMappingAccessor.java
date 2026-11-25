package mod.kelvinlby.opencrafter.mixin.input;

import net.minecraft.client.ToggleKeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.function.BooleanSupplier;

/** Exposes whether a toggle mapping is currently in toggle mode (e.g. the "Sneak: Toggle" option). */
@Mixin(ToggleKeyMapping.class)
public interface ToggleKeyMappingAccessor {
	@Accessor("needsToggle")
	BooleanSupplier opencrafter$needsToggle();
}
