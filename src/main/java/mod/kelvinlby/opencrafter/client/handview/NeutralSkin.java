package mod.kelvinlby.opencrafter.client.handview;

import net.minecraft.core.ClientAsset;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.PlayerModelType;
import net.minecraft.world.entity.player.PlayerSkin;

/** The one skin every player's arm shows when skins are removed: slim Alex, whose arm is narrower than Steve's. */
final class NeutralSkin {
	static final PlayerSkin ARM = new PlayerSkin(
		new ClientAsset.ResourceTexture(Identifier.withDefaultNamespace("entity/player/slim/alex")), null, null, PlayerModelType.SLIM, true);

	private NeutralSkin() {
	}
}
