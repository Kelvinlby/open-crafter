package mod.kelvinlby.opencrafter.encoder.mixin;

import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Replay: a block break finishes when the recorded client's did. */
@Mixin(MultiPlayerGameMode.class)
public interface MultiPlayerGameModeAccessor {
	@Accessor("destroyProgress")
	float openCrafterEncoder$destroyProgress();

	@Accessor("destroyProgress")
	void openCrafterEncoder$setDestroyProgress(float progress);

	@Accessor("destroyDelay")
	int openCrafterEncoder$destroyDelay();

	@Accessor("destroyDelay")
	void openCrafterEncoder$setDestroyDelay(int delay);
}
