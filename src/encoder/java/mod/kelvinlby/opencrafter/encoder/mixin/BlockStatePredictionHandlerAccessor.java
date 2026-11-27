package mod.kelvinlby.opencrafter.encoder.mixin;

import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Replay: re-enacted predictions take the recorded sequence numbers. */
@Mixin(BlockStatePredictionHandler.class)
public interface BlockStatePredictionHandlerAccessor {
	@Accessor("currentSequenceNr")
	void openCrafterEncoder$setCurrentSequenceNr(int sequence);
}
