package mod.kelvinlby.crafter.browser;

import net.ccbluex.liquidbounce.mcef.MCEF;
import net.ccbluex.liquidbounce.mcef.cef.MCEFBrowser;
import net.ccbluex.liquidbounce.mcef.cef.MCEFBrowserSettings;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.CharInput;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A Minecraft Screen that displays a full-screen Chromium browser via MCEF.
 */
public class BrowserScreen extends Screen {
    private static final Logger LOGGER = LoggerFactory.getLogger(BrowserScreen.class);
    private MCEFBrowser browser;
    private final String url;

    public BrowserScreen(String url) {
        super(Text.literal("Browser"));
        this.url = url;
    }

    @Override
    protected void init() {
        super.init();

        int w = client.getWindow().getFramebufferWidth();
        int h = client.getWindow().getFramebufferHeight();

        if (browser != null) {
            browser.resize(w, h);
            return;
        }

        // Create off-screen browser at window resolution
        // GPU acceleration disabled for Linux compatibility (no EGL context for dmabuf)
        browser = MCEF.INSTANCE.createBrowser(
                url,
                true,  // off-screen rendering
                w,
                h,
                new MCEFBrowserSettings(60, false)  // Disable GPU acceleration for software rendering
        );

        // Initialize the renderer to create the texture
        // This must be called after browser creation
        browser.getRenderer().initialize();

        browser.loadURL(url);
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        BrowserManager.tick();

        if (browser == null) {
            super.render(context, mouseX, mouseY, delta);
            context.drawCenteredTextWithShadow(
                    textRenderer, Text.literal("Loading browser..."),
                    width / 2, height / 2, 0xFFFFFF
            );
            return;
        }

        // Check if texture is ready and has been painted at least once
        if (!browser.getRenderer().isTextureReady() || browser.getRenderer().isUnpainted()) {
            super.render(context, mouseX, mouseY, delta);
            context.drawCenteredTextWithShadow(
                    textRenderer, Text.literal("Loading browser..."),
                    width / 2, height / 2, 0xFFFFFF
            );
            return;
        }

        // Get the texture identifier and verify it's usable
        var textureId = browser.getRenderer().getIdentifier();
        if (textureId == null) {
            super.render(context, mouseX, mouseY, delta);
            context.drawCenteredTextWithShadow(
                    textRenderer, Text.literal("Loading browser..."),
                    width / 2, height / 2, 0xFFFFFF
            );
            return;
        }

        // Check if the texture has a valid GL texture ID
        // getTextureId() returns 0 if the texture isn't a GL texture
        int glTextureId = browser.getRenderer().getTextureId();
        if (glTextureId <= 0) {
            // Texture exists but isn't a valid GL texture - this can happen on some Linux systems
            // Try to continue anyway, as the texture might still be renderable
            LOGGER.warn("Browser texture has invalid GL ID: {}, attempting to render anyway", glTextureId);
        }

        try {
            // Draw the browser texture as a fullscreen quad
            context.drawTexturedQuad(textureId, 0, 0, width, height, 0f, 1f, 0f, 1f);
        } catch (IllegalStateException e) {
            // Texture view doesn't exist yet - still loading
            super.render(context, mouseX, mouseY, delta);
            context.drawCenteredTextWithShadow(
                    textRenderer, Text.literal("Loading browser..."),
                    width / 2, height / 2, 0xFFFFFF
            );
        }
    }

    // --- Input forwarding ---

    @Override
    public boolean mouseClicked(Click click, boolean bl) {
        if (browser != null) {
            browser.setFocus(true);
            int s = client.getWindow().getScaleFactor();
            browser.sendMousePress((int) (click.x() * s), (int) (click.y() * s), click.button());
        }
        return super.mouseClicked(click, bl);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (browser != null) {
            int s = client.getWindow().getScaleFactor();
            browser.sendMouseRelease((int) (click.x() * s), (int) (click.y() * s), click.button());
        }
        return super.mouseReleased(click);
    }

    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        if (browser != null) {
            int s = client.getWindow().getScaleFactor();
            browser.sendMouseMove((int) (mouseX * s), (int) (mouseY * s));
        }
        super.mouseMoved(mouseX, mouseY);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY,
                                  double horizontalAmount, double verticalAmount) {
        if (browser != null) {
            int s = client.getWindow().getScaleFactor();
            browser.sendMouseWheel((int) (mouseX * s), (int) (mouseY * s), verticalAmount);
        }
        return super.mouseScrolled(mouseX, mouseY, horizontalAmount, verticalAmount);
    }

    @Override
    public boolean keyPressed(KeyInput keyInput) {
        if (keyInput.key() == GLFW.GLFW_KEY_ESCAPE) {
            close();
            return true;
        }
        if (browser != null) {
            browser.setFocus(true);
            browser.sendKeyPress(keyInput.key(), keyInput.scancode(), keyInput.modifiers());
        }
        return true;
    }

    @Override
    public boolean keyReleased(KeyInput keyInput) {
        if (browser != null) {
            browser.sendKeyRelease(keyInput.key(), keyInput.scancode(), keyInput.modifiers());
        }
        return true;
    }

    @Override
    public boolean charTyped(CharInput charInput) {
        if (browser != null) {
            browser.setFocus(true);
            browser.sendKeyTyped((char) charInput.codepoint(), charInput.modifiers());
        }
        return true;
    }

    @Override
    public void close() {
        if (browser != null) {
            browser.close();
            browser = null;
        }
        super.close();
    }
}
