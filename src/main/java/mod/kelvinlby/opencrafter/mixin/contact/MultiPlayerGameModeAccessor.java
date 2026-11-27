package mod.kelvinlby.opencrafter.mixin.contact;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Contact effects: which block the game's own break is on (to leave its cracks alone). */
@Mixin(MultiPlayerGameMode.class)
public interface MultiPlayerGameModeAccessor {
	@Accessor("destroyBlockPos")
	BlockPos openCrafter$destroyBlockPos();
}
