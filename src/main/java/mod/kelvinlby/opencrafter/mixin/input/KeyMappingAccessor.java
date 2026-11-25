package mod.kelvinlby.opencrafter.mixin.input;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exposes the per-mapping click counter and bound key, so a click can be simulated on one mapping. */
@Mixin(KeyMapping.class)
public interface KeyMappingAccessor {
	@Accessor("clickCount")
	int opencrafter$getClickCount();

	@Accessor("clickCount")
	void opencrafter$setClickCount(int clickCount);

	@Accessor("key")
	InputConstants.Key opencrafter$getKey();
}
