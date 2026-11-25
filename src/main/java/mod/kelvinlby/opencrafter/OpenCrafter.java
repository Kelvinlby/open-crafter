package mod.kelvinlby.opencrafter;

import net.fabricmc.api.ModInitializer;
import net.minecraft.resources.Identifier;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Common entrypoint. All features are client-side; this only exists so the mod loads anywhere. */
public class OpenCrafter implements ModInitializer {
	public static final String MOD_ID = "open-crafter";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitialize() {
	}

	public static Identifier id(String path) {
		return Identifier.fromNamespaceAndPath(MOD_ID, path);
	}
}
