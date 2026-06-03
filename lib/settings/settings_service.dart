import 'package:flutter/material.dart';
import 'package:shared_preferences/shared_preferences.dart';


/// A named entry in the theme color picker.
class ThemeColorOption {
  const ThemeColorOption(this.label, this.color);

  final String label;
  final Color color;
}


/// Loads, exposes, and persists user-customizable settings.
///
/// Created once in `main()` and threaded down the widget tree by constructor.
/// The theme [seedColor] is exposed as a [ValueNotifier] so the app theme can
/// rebuild live when it changes. [uiScale] is read once at launch and only
/// takes effect on restart, matching the "restart to apply" UX for scaling.
class SettingsService {
  static const String _kSeedColor = 'ui_seed_color';
  static const String _kUiScale = 'ui_scale';

  static const Color _defaultSeedColor = Colors.deepPurple;
  static const double defaultUiScale = 1.0;

  /// UI scale bounds and step, shared with the settings slider.
  static const double minUiScale = 0.5;
  static const double maxUiScale = 4.0;
  static const double uiScaleStep = 0.25;

  /// Standard Material Design primary swatches offered in the color picker.
  static const List<ThemeColorOption> themeColors = <ThemeColorOption>[
    ThemeColorOption('Deep Purple', Colors.deepPurple),
    ThemeColorOption('Purple', Colors.purple),
    ThemeColorOption('Indigo', Colors.indigo),
    ThemeColorOption('Blue', Colors.blue),
    ThemeColorOption('Cyan', Colors.cyan),
    ThemeColorOption('Teal', Colors.teal),
    ThemeColorOption('Green', Colors.green),
    ThemeColorOption('Lime', Colors.lime),
    ThemeColorOption('Amber', Colors.amber),
    ThemeColorOption('Orange', Colors.orange),
    ThemeColorOption('Deep Orange', Colors.deepOrange),
    ThemeColorOption('Red', Colors.red),
    ThemeColorOption('Pink', Colors.pink),
    ThemeColorOption('Brown', Colors.brown),
    ThemeColorOption('Blue Grey', Colors.blueGrey),
  ];

  late final SharedPreferences _prefs;

  /// Live theme seed color. Updating it via [setSeedColor] notifies listeners.
  final ValueNotifier<Color> seedColor = ValueNotifier<Color>(_defaultSeedColor);

  /// The UI scale that was active at launch. Applied app-wide; does not change
  /// live (see [setUiScale]).
  double _uiScale = defaultUiScale;
  double get uiScale => _uiScale;

  /// The currently persisted scale, which may differ from the active [uiScale]
  /// if the user changed it this session without restarting yet.
  double _pendingUiScale = defaultUiScale;
  double get pendingUiScale => _pendingUiScale;

  /// Reads stored settings. Call once before `runApp`.
  Future<void> load() async {
    _prefs = await SharedPreferences.getInstance();

    final int? storedColor = _prefs.getInt(_kSeedColor);
    if (storedColor != null) {
      seedColor.value = Color(storedColor);
    }

    final double storedScale = _prefs.getDouble(_kUiScale) ?? defaultUiScale;
    _uiScale = storedScale;
    _pendingUiScale = storedScale;
  }

  /// Updates the theme color live and persists it.
  Future<void> setSeedColor(Color color) async {
    seedColor.value = color;
    await _prefs.setInt(_kSeedColor, color.toARGB32());
  }

  /// Persists a new UI scale. Takes effect on the next app restart.
  Future<void> setUiScale(double scale) async {
    _pendingUiScale = scale;
    await _prefs.setDouble(_kUiScale, scale);
  }
}
