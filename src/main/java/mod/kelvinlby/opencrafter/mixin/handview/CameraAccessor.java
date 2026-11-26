package mod.kelvinlby.opencrafter.mixin.handview;

import net.minecraft.client.Camera;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The field of view the first-person hands are drawn with. */
@Mixin(Camera.class)
public interface CameraAccessor {
	@Accessor("hudFov")
	float openCrafter$hudFov();
}
