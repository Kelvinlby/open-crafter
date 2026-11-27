package mod.kelvinlby.opencrafter.encoder.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.network.Connection;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Replay: the recording's connection is ticked by the game like any connection being set up. */
@Mixin(Minecraft.class)
public interface MinecraftAccessor {
	@Accessor("pendingConnection")
	void openCrafterEncoder$setPendingConnection(Connection connection);
}
