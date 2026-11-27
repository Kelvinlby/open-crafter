package mod.kelvinlby.opencrafter.client.inventoryview;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import org.joml.Matrix3x2fStack;
import org.jspecify.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Optional;

/**
 * Static entry points for the inventory view mixins. Each leaves vanilla's behaviour until
 * {@link InventoryViewModule} is installed and while no flight is running. Render thread only.
 */
public final class InventoryViewHooks {
	private static @Nullable BackpackFlight flight;
	/** The screen before each running setScreen call; it re-enters itself while closing a container. */
	private static final Deque<Optional<Screen>> screensBeforeChange = new ArrayDeque<>();
	/** The offset of the screen being drawn, while it is drawn. */
	private static int drawnOffset;

	private InventoryViewHooks() {
	}

	static void install(BackpackFlight backpackFlight) {
		flight = backpackFlight;
	}

	/** One of vanilla's screen drawing steps (background, contents, deferred tooltips), as the mixin hands it over. */
	@FunctionalInterface
	public interface ScreenDraw {
		void draw(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks);
	}

	/** Vanilla's gradient fill, as the mixin hands it over. */
	@FunctionalInterface
	public interface GradientFill {
		void fill(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int topColor, int bottomColor);
	}

	// ---- screen changes (Gui.setScreen) ----

	public static void beforeScreenChange(@Nullable Screen old) {
		screensBeforeChange.push(Optional.ofNullable(old));
	}

	public static void afterScreenChange(@Nullable Screen current) {
		Screen old = screensBeforeChange.pop().orElse(null);
		if (flight != null) {
			flight.onScreenChanged(old, current);
		}
	}

	// ---- drawing ----

	/**
	 * Draws one of a screen's own drawing steps moved down by its flight offset; the mouse moves
	 * with it so hovering matches.
	 */
	public static void drawMoved(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks, ScreenDraw vanilla) {
		int offset = flight == null ? 0 : flight.offset(screen);
		if (offset == 0) {
			vanilla.draw(graphics, mouseX, mouseY, partialTicks);
			return;
		}
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(0, offset);
		drawnOffset = offset;
		try {
			vanilla.draw(graphics, mouseX, mouseY - offset, partialTicks);
		} finally {
			drawnOffset = 0;
			pose.popMatrix();
		}
	}

	/** The dim backdrop stays in place (it covers the whole window) and fades with the flight. */
	public static void drawDim(Screen screen, GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int top, int bottom, GradientFill vanilla) {
		if (drawnOffset == 0 || flight == null) {
			vanilla.fill(graphics, x0, y0, x1, y1, top, bottom);
			return;
		}
		float strength = flight.dimStrength(screen);
		Matrix3x2fStack pose = graphics.pose();
		pose.pushMatrix();
		pose.translate(0, -drawnOffset);
		try {
			vanilla.fill(graphics, x0, y0, x1, y1, fade(top, strength), fade(bottom, strength));
		} finally {
			pose.popMatrix();
		}
	}

	/**
	 * The inventory's player model is drawn into an absolute screen box that ignores the pose, so
	 * the box (and the mouse it looks at) moves by the offset explicitly.
	 */
	public static int entityBoxOffset() {
		return drawnOffset;
	}

	/** Draws the closed backpack while it flies out, once no other screen is open. */
	public static void drawClosingScreen(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker) {
		Minecraft minecraft = Minecraft.getInstance();
		Screen closing = flight == null ? null : flight.closingScreen();
		if (closing == null || minecraft.gui.screen() != null || minecraft.player == null) {
			return;
		}
		int mouseX = (int) minecraft.mouseHandler.getScaledXPos(minecraft.getWindow());
		int mouseY = (int) minecraft.mouseHandler.getScaledYPos(minecraft.getWindow());
		drawScreenOnly(closing, graphics, mouseX, mouseY, deltaTracker.getGameTimeDeltaTicks());
	}

	/**
	 * Vanilla's screen drawing steps without the screen draw method itself, so what other mods
	 * hook into that method (overlays meant for an open screen) is not drawn for a closed one.
	 */
	private static void drawScreenOnly(Screen screen, GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTicks) {
		graphics.nextStratum();
		drawMoved(screen, graphics, mouseX, mouseY, partialTicks, screen::extractBackground);
		graphics.nextStratum();
		drawMoved(screen, graphics, mouseX, mouseY, partialTicks, screen::extractRenderState);
		drawMoved(screen, graphics, mouseX, mouseY, partialTicks, GuiGraphicsExtractor::extractDeferredElements);
	}

	private static int fade(int argb, float strength) {
		int alpha = Math.round((argb >>> 24) * strength);
		return alpha << 24 | argb & 0xFFFFFF;
	}
}
