import 'dart:io';

import 'package:flutter/material.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';
import 'package:shared_preferences/shared_preferences.dart';


/// A named entry in the theme color picker.
class ThemeColorOption {
  const ThemeColorOption(this.label, this.color);

  final String label;
  final Color color;
}


/// The time unit used by the conversation auto-clean retention period.
enum RetentionUnit { days, months }


/// Loads, exposes, and persists user-customizable settings.
///
/// Created once in `main()` and threaded down the widget tree by constructor.
/// The theme [seedColor] is exposed as a [ValueNotifier] so the app theme can
/// rebuild live when it changes. [uiScale] is read once at launch and only
/// takes effect on restart, matching the "restart to apply" UX for scaling.
class SettingsService {
  static const String _kSeedColor = 'ui_seed_color';
  static const String _kUiScale = 'ui_scale';
  static const String _kConversationDir = 'conversation_dir';
  static const String _kAutoCleanEnabled = 'conversation_auto_clean';
  static const String _kAutoCleanValue = 'conversation_auto_clean_value';
  static const String _kAutoCleanUnit = 'conversation_auto_clean_unit';
  static const String _kModelDir = 'model_dir';
  static const String _kConvListFraction = 'conversation_list_fraction';

  static const Color _defaultSeedColor = Colors.deepPurple;
  static const double defaultUiScale = 1.0;

  /// Default auto-clean retention: conversations older than 30 days.
  static const int defaultAutoCleanValue = 30;
  static const RetentionUnit defaultAutoCleanUnit = RetentionUnit.days;

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

  /// Folder where conversations are stored. Defaults to `<appSupport>/conversation`.
  String _conversationDir = '';
  String get conversationDir => _conversationDir;

  /// Whether old conversations are auto-deleted. Off by default.
  bool _autoCleanEnabled = false;
  bool get autoCleanEnabled => _autoCleanEnabled;

  /// Retention period count (paired with [autoCleanUnit]).
  int _autoCleanValue = defaultAutoCleanValue;
  int get autoCleanValue => _autoCleanValue;

  /// Retention period unit (days or months).
  RetentionUnit _autoCleanUnit = defaultAutoCleanUnit;
  RetentionUnit get autoCleanUnit => _autoCleanUnit;

  /// The instant before which conversations are eligible for auto-clean.
  /// Returns null when auto-clean is disabled. [now] is injectable for tests.
  DateTime? autoCleanCutoff([DateTime? now]) {
    if (!_autoCleanEnabled) return null;
    final DateTime base = now ?? DateTime.now();
    switch (_autoCleanUnit) {
      case RetentionUnit.days:
        return base.subtract(Duration(days: _autoCleanValue));
      case RetentionUnit.months:
        return DateTime(base.year, base.month - _autoCleanValue, base.day,
            base.hour, base.minute, base.second, base.millisecond);
    }
  }

  /// Folder where downloaded models are stored. Defaults to `<appSupport>/model`.
  String _modelDir = '';
  String get modelDir => _modelDir;

  /// Fraction of the Home page width occupied by the conversation list pane,
  /// adjusted by dragging the divider. Clamped to a sensible range.
  static const double defaultConvListFraction = 0.25;
  static const double minConvListFraction = 0.15;
  static const double maxConvListFraction = 0.5;
  double _convListFraction = defaultConvListFraction;
  double get convListFraction => _convListFraction;

  /// Reads stored settings. Call once before `runApp`.
  Future<void> load() async {
    _prefs = await SharedPreferences.getInstance();

    final Directory support = await getApplicationSupportDirectory();
    final String base = support.path;

    final int? storedColor = _prefs.getInt(_kSeedColor);
    if (storedColor != null) {
      seedColor.value = Color(storedColor);
    }

    final double storedScale = _prefs.getDouble(_kUiScale) ?? defaultUiScale;
    _uiScale = storedScale;
    _pendingUiScale = storedScale;

    _conversationDir =
        _prefs.getString(_kConversationDir) ?? p.join(base, 'conversation');
    _modelDir = _prefs.getString(_kModelDir) ?? p.join(base, 'model');

    _convListFraction =
        (_prefs.getDouble(_kConvListFraction) ?? defaultConvListFraction)
            .clamp(minConvListFraction, maxConvListFraction);

    _autoCleanEnabled = _prefs.getBool(_kAutoCleanEnabled) ?? false;
    _autoCleanValue = _prefs.getInt(_kAutoCleanValue) ?? defaultAutoCleanValue;
    _autoCleanUnit = RetentionUnit.values.firstWhere(
      (RetentionUnit u) => u.name == _prefs.getString(_kAutoCleanUnit),
      orElse: () => defaultAutoCleanUnit,
    );
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

  /// Persists the conversation storage folder.
  Future<void> setConversationDir(String dir) async {
    _conversationDir = dir;
    await _prefs.setString(_kConversationDir, dir);
  }

  /// Persists the model storage folder.
  Future<void> setModelDir(String dir) async {
    _modelDir = dir;
    await _prefs.setString(_kModelDir, dir);
  }

  /// Persists the conversation list pane width fraction (clamped).
  Future<void> setConvListFraction(double fraction) async {
    _convListFraction =
        fraction.clamp(minConvListFraction, maxConvListFraction);
    await _prefs.setDouble(_kConvListFraction, _convListFraction);
  }

  /// Persists whether old conversations are auto-cleaned.
  Future<void> setAutoCleanEnabled(bool enabled) async {
    _autoCleanEnabled = enabled;
    await _prefs.setBool(_kAutoCleanEnabled, enabled);
  }

  /// Persists the auto-clean retention count.
  Future<void> setAutoCleanValue(int value) async {
    _autoCleanValue = value;
    await _prefs.setInt(_kAutoCleanValue, value);
  }

  /// Persists the auto-clean retention unit.
  Future<void> setAutoCleanUnit(RetentionUnit unit) async {
    _autoCleanUnit = unit;
    await _prefs.setString(_kAutoCleanUnit, unit.name);
  }
}
