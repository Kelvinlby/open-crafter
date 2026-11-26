package mod.kelvinlby.opencrafter.mixin.handview;

import net.minecraft.client.player.LocalPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** The rotation last sent to the server. */
@Mixin(LocalPlayer.class)
public interface LocalPlayerAccessor {
	@Accessor("yRotLast")
	float openCrafter$yRotLast();

	@Accessor("xRotLast")
	float openCrafter$xRotLast();
}
