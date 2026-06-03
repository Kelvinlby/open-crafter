import 'package:flutter/material.dart';
import '../settings/settings_service.dart';


class SettingPage extends StatefulWidget {
  const SettingPage({super.key, required this.settings});

  final SettingsService settings;

  @override
  State<SettingPage> createState() => _SettingPageState();
}


class _SettingPageState extends State<SettingPage> {
  late double _pendingScale;

  @override
  void initState() {
    super.initState();
    _pendingScale = widget.settings.pendingUiScale;
  }

  void _onColorSelected(Color color) {
    widget.settings.setSeedColor(color);
    setState(() {}); // Refresh the selected-swatch indicator.
  }

  void _onScaleChangeEnd(double value) {
    widget.settings.setUiScale(value);
    final ScaffoldMessengerState messenger = ScaffoldMessenger.of(context);
    messenger.clearSnackBars();
    messenger.showSnackBar(
      const SnackBar(
        content: Text('Restart the app to apply the new UI scale.'),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    return ListView(
      padding: const EdgeInsets.symmetric(vertical: 16),
      children: <Widget>[
        _Section(
          title: 'UI',
          children: <Widget>[
            _buildThemeColorItem(context),
            _buildUiScaleItem(context),
          ],
        ),
      ],
    );
  }

  Widget _buildThemeColorItem(BuildContext context) {
    final Color current = widget.settings.seedColor.value;
    return _SettingItem(
      title: 'Theme color',
      subtitle: 'Seed color used to generate the app color scheme.',
      child: Wrap(
        spacing: 12,
        runSpacing: 12,
        children: <Widget>[
          for (final ThemeColorOption option in SettingsService.themeColors)
            _ColorSwatch(
              option: option,
              selected: option.color.toARGB32() == current.toARGB32(),
              onTap: () => _onColorSelected(option.color),
            ),
        ],
      ),
    );
  }

  Widget _buildUiScaleItem(BuildContext context) {
    return _SettingItem(
      title: 'UI Scale',
      subtitle: 'Scales all text and icons. Takes effect after restart.',
      trailing: Text(
        '${_pendingScale.toStringAsFixed(2)}×',
        style: Theme.of(context).textTheme.titleMedium,
      ),
      child: Slider(
        value: _pendingScale,
        min: SettingsService.minUiScale,
        max: SettingsService.maxUiScale,
        // (4.0 - 0.5) / 0.25 = 14 steps of 0.25.
        divisions: ((SettingsService.maxUiScale - SettingsService.minUiScale) /
                SettingsService.uiScaleStep)
            .round(),
        label: '${_pendingScale.toStringAsFixed(2)}×',
        onChanged: (double value) {
          setState(() => _pendingScale = value);
        },
        onChangeEnd: _onScaleChangeEnd,
      ),
    );
  }
}


/// A titled group of settings rendered top-to-bottom.
class _Section extends StatelessWidget {
  const _Section({required this.title, required this.children});

  final String title;
  final List<Widget> children;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: <Widget>[
        Padding(
          padding: const EdgeInsets.fromLTRB(20, 8, 20, 8),
          child: Text(
            title,
            style: theme.textTheme.titleSmall?.copyWith(
              color: theme.colorScheme.primary,
              fontWeight: FontWeight.bold,
            ),
          ),
        ),
        ...children,
        const Divider(height: 24),
      ],
    );
  }
}


/// A single setting row: title + optional subtitle/trailing, with its control
/// laid out beneath.
class _SettingItem extends StatelessWidget {
  const _SettingItem({
    required this.title,
    required this.child,
    this.subtitle,
    this.trailing,
  });

  final String title;
  final String? subtitle;
  final Widget? trailing;
  final Widget child;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: <Widget>[
          Row(
            children: <Widget>[
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: <Widget>[
                    Text(title, style: theme.textTheme.titleMedium),
                    if (subtitle case final String text) ...<Widget>[
                      const SizedBox(height: 2),
                      Text(
                        text,
                        style: theme.textTheme.bodySmall?.copyWith(
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                      ),
                    ],
                  ],
                ),
              ),
              ?trailing,
            ],
          ),
          const SizedBox(height: 12),
          child,
        ],
      ),
    );
  }
}


/// A circular, selectable color swatch for the theme color picker.
class _ColorSwatch extends StatelessWidget {
  const _ColorSwatch({
    required this.option,
    required this.selected,
    required this.onTap,
  });

  final ThemeColorOption option;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    return Tooltip(
      message: option.label,
      child: InkWell(
        onTap: onTap,
        customBorder: const CircleBorder(),
        child: Container(
          width: 40,
          height: 40,
          decoration: BoxDecoration(
            color: option.color,
            shape: BoxShape.circle,
            border: Border.all(
              color: selected ? theme.colorScheme.onSurface : Colors.transparent,
              width: 3,
            ),
          ),
          child: selected
              ? const Icon(Icons.check, color: Colors.white, size: 20)
              : null,
        ),
      ),
    );
  }
}
