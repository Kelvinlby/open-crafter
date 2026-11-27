package mod.kelvinlby.opencrafter.encoder.mixin;

import net.minecraft.client.DeltaTracker;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Replay: the first captured frame falls at the configured point within its tick. */
@Mixin(DeltaTracker.Timer.class)
public interface DeltaTrackerTimerAccessor {
	@Accessor("lastMs")
	void openCrafterEncoder$setLastMs(long lastMs);

	@Accessor("deltaTickResidual")
	void openCrafterEncoder$setDeltaTickResidual(float residual);
}
