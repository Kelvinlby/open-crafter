package mod.kelvinlby.opencrafter.encoder.mixin;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.prediction.BlockStatePredictionHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Replay: reaches the level's block prediction bookkeeping. */
@Mixin(ClientLevel.class)
public interface ClientLevelAccessor {
	@Accessor("blockStatePredictionHandler")
	BlockStatePredictionHandler openCrafterEncoder$predictionHandler();
}
