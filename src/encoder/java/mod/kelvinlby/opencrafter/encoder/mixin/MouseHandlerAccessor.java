package mod.kelvinlby.opencrafter.encoder.mixin;

import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Replay: puts the pointer where the recorded client clicked. */
@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {
	@Accessor("xpos")
	void openCrafterEncoder$setXpos(double xpos);

	@Accessor("ypos")
	void openCrafterEncoder$setYpos(double ypos);
}
