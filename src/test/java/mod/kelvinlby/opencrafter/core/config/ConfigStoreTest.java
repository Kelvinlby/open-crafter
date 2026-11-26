package mod.kelvinlby.opencrafter.core.config;

import mod.kelvinlby.opencrafter.core.config.handview.SwingConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigStoreTest {
	@TempDir
	Path dir;

	@Test
	void copiesAreIndependentOfTheStore() {
		ConfigStore store = ConfigStore.load(dir.resolve("c.json"));
		OpenCrafterConfig draft = store.copy();
		draft.handView.enabled = true;
		assertFalse(store.get().handView.enabled);
		assertNotSame(store.get().handView, draft.handView);
	}

	@Test
	void replacePersistsAndTellsListenersOldAndNew() throws Exception {
		Path file = dir.resolve("c.json");
		ConfigStore store = ConfigStore.load(file);
		List<String> seen = new ArrayList<>();
		store.onReplace((old, edited) -> seen.add(old.handView.swing.leftClick + "->" + edited.handView.swing.leftClick));
		OpenCrafterConfig draft = store.copy();
		draft.handView.swing.leftClick = SwingConfig.LeftClickAnimation.STUB;
		store.replace(draft);
		assertEquals(List.of("VANILLA->STUB"), seen);
		assertTrue(Files.readString(file).contains("\"STUB\""));
		assertEquals(SwingConfig.LeftClickAnimation.STUB, ConfigStore.load(file).get().handView.swing.leftClick);
	}

	@Test
	void oldFilesWithoutHandViewGetDefaults() throws Exception {
		Path file = dir.resolve("c.json");
		Files.writeString(file, "{\"vision\":{\"rateMode\":\"TICK\",\"fixedHz\":5.0}}");
		OpenCrafterConfig config = ConfigStore.load(file).get();
		assertEquals(VisionConfig.RateMode.TICK, config.vision.rateMode);
		assertFalse(config.handView.enabled);
		assertEquals(SwingConfig.VANILLA_SWING_TICKS, config.handView.swing.swingTicks);
	}

	@Test
	void transportComparisonSeesEveryField() {
		TransportConfig a = new TransportConfig();
		TransportConfig b = a.copy();
		assertTrue(a.sameAs(b));
		b.tcpPort = 1234;
		assertFalse(a.sameAs(b));
	}
}
