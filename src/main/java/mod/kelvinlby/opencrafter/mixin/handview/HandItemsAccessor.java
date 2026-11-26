package mod.kelvinlby.opencrafter.mixin.handview;

import net.minecraft.client.player.FirstPersonHandsAndItems;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Hand view: how far each held item is raised, to start a re-equip after a block morph. */
@Mixin(FirstPersonHandsAndItems.class)
public interface HandItemsAccessor {
	@Accessor("mainHandHeight")
	void openCrafter$setMainHandHeight(float height);

	@Accessor("oMainHandHeight")
	void openCrafter$setOldMainHandHeight(float height);

	@Accessor("offHandHeight")
	void openCrafter$setOffHandHeight(float height);

	@Accessor("oOffHandHeight")
	void openCrafter$setOldOffHandHeight(float height);
}
