package mod.kelvinlby.crafter.screen;

import io.wispforest.owo.ui.base.BaseUIModelScreen;
import io.wispforest.owo.ui.component.*;
import io.wispforest.owo.ui.container.FlowLayout;
import io.wispforest.owo.ui.core.OwoUIAdapter;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * Data-driven settings screen using owo-lib UI models.
 * The UI structure is defined in assets/open-crafter/owo_ui/settings.xml
 */
public class SettingScreen extends BaseUIModelScreen<FlowLayout> {

    // Settings data store
    private final Map<String, Object> settingsData = new HashMap<>();

    // Component references
    private TextBoxComponent playerNameInput;
    private SliderComponent volumeSlider;
    private LabelComponent volumeValueLabel;
    private CheckboxComponent notificationsCheckbox;
    private CheckboxComponent autoSaveCheckbox;
    private CheckboxComponent darkModeCheckbox;

    public SettingScreen() {
        super(FlowLayout.class, BaseUIModelScreen.DataSource.asset(Identifier.of("open-crafter", "settings")));
        
        // Initialize default settings data
        loadDefaultSettings();
    }

    /**
     * Constructor with parent screen for back navigation
     */
    public SettingScreen(Screen parent) {
        this();
    }

    private void loadDefaultSettings() {
        settingsData.put("playerName", "");
        settingsData.put("volume", 50);
        settingsData.put("enableNotifications", true);
        settingsData.put("autoSave", false);
        settingsData.put("darkMode", false);
        settingsData.put("difficulty", "Normal");
    }

    @Override
    protected @NotNull OwoUIAdapter<FlowLayout> createAdapter() {
        return this.model.createAdapter(this.rootComponentClass, this);
    }

    @Override
    protected void build(FlowLayout rootComponent) {
        // Retrieve components by ID from the XML model
        this.playerNameInput = rootComponent.childById(TextBoxComponent.class, "player-name-input");
        this.volumeSlider = rootComponent.childById(SliderComponent.class, "volume-slider");
        this.volumeValueLabel = rootComponent.childById(LabelComponent.class, "volume-value");
        this.notificationsCheckbox = rootComponent.childById(CheckboxComponent.class, "enable-notifications");
        this.autoSaveCheckbox = rootComponent.childById(CheckboxComponent.class, "auto-save");
        this.darkModeCheckbox = rootComponent.childById(CheckboxComponent.class, "dark-mode");

        // Bind data to components
        bindDataToComponents();

        // Set up event listeners
        setupEventListeners(rootComponent);
    }

    /**
     * Bind settings data to UI components
     */
    private void bindDataToComponents() {
        if (playerNameInput != null) {
            playerNameInput.text((String) settingsData.get("playerName"));
        }

        if (volumeSlider != null) {
            int volume = (Integer) settingsData.get("volume");
            volumeSlider.value(volume / 100.0);
        }

        updateVolumeLabel();

        if (notificationsCheckbox != null) {
            notificationsCheckbox.checked((Boolean) settingsData.get("enableNotifications"));
        }

        if (autoSaveCheckbox != null) {
            autoSaveCheckbox.checked((Boolean) settingsData.get("autoSave"));
        }

        if (darkModeCheckbox != null) {
            darkModeCheckbox.checked((Boolean) settingsData.get("darkMode"));
        }
    }

    private void updateVolumeLabel() {
        if (volumeValueLabel != null) {
            volumeValueLabel.text(Text.literal(String.valueOf(settingsData.get("volume"))));
        }
    }

    /**
     * Set up event listeners for interactive components
     */
    private void setupEventListeners(FlowLayout rootComponent) {
        // Volume slider change listener
        if (volumeSlider != null) {
            volumeSlider.onChanged().subscribe(value -> {
                int volume = (int) (value * 100);
                settingsData.put("volume", volume);
                updateVolumeLabel();
            });
        }

        // Player name input listener
        if (playerNameInput != null) {
            playerNameInput.onChanged().subscribe(text -> {
                settingsData.put("playerName", text);
            });
        }

        // Checkbox listeners
        if (notificationsCheckbox != null) {
            notificationsCheckbox.onChanged(checked -> {
                settingsData.put("enableNotifications", checked);
            });
        }

        if (autoSaveCheckbox != null) {
            autoSaveCheckbox.onChanged(checked -> {
                settingsData.put("autoSave", checked);
            });
        }

        if (darkModeCheckbox != null) {
            darkModeCheckbox.onChanged(checked -> {
                settingsData.put("darkMode", checked);
            });
        }

        // Save button
        ButtonComponent saveButton = rootComponent.childById(ButtonComponent.class, "save-button");
        if (saveButton != null) {
            saveButton.onPress(button -> {
                saveSettings();
            });
        }

        // Reset button
        ButtonComponent resetButton = rootComponent.childById(ButtonComponent.class, "reset-button");
        if (resetButton != null) {
            resetButton.onPress(button -> {
                loadDefaultSettings();
                bindDataToComponents();
            });
        }

        // Close button
        ButtonComponent closeButton = rootComponent.childById(ButtonComponent.class, "close-button");
        if (closeButton != null) {
            closeButton.onPress(button -> close());
        }
    }

    /**
     * Save settings to persistent storage
     */
    private void saveSettings() {
        // TODO: Implement persistent storage (e.g., JSON config file)
        mod.kelvinlby.crafter.OpenCrafter.LOGGER.info("Saving settings: {}", settingsData);
    }

    /**
     * Get a setting value by key
     */
    public Object getSetting(String key) {
        return settingsData.get(key);
    }

    /**
     * Set a setting value and update UI
     */
    public void setSetting(String key, Object value) {
        settingsData.put(key, value);
        bindDataToComponents();
    }

    /**
     * Get all settings as a map
     */
    public Map<String, Object> getAllSettings() {
        return new HashMap<>(settingsData);
    }
}
