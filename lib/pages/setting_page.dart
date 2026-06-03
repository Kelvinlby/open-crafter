import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import '../settings/settings_service.dart';


class SettingPage extends StatefulWidget {
  const SettingPage({super.key, required this.settings});

  final SettingsService settings;

  @override
  State<SettingPage> createState() => _SettingPageState();
}


class _SettingPageState extends State<SettingPage> {
  late double _pendingScale;
  late String _conversationDir;
  late bool _autoCleanEnabled;
  late RetentionUnit _autoCleanUnit;
  late String _modelDir;
  late final TextEditingController _retentionController;

  @override
  void initState() {
    super.initState();
    final SettingsService s = widget.settings;
    _pendingScale = s.pendingUiScale;
    _conversationDir = s.conversationDir;
    _autoCleanEnabled = s.autoCleanEnabled;
    _autoCleanUnit = s.autoCleanUnit;
    _modelDir = s.modelDir;
    _retentionController =
        TextEditingController(text: s.autoCleanValue.toString());
  }

  @override
  void dispose() {
    _retentionController.dispose();
    super.dispose();
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
          title: 'Conversation',
          children: <Widget>[
            _buildSaveFolderItem(context),
            _buildAutoCleanItem(context),
            if (_autoCleanEnabled) _buildRetentionItem(context),
          ],
        ),
        _Section(
          title: 'Model & Runtime',
          children: <Widget>[
            _buildModelFolderItem(context),
          ],
        ),
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

  Widget _buildSaveFolderItem(BuildContext context) {
    return _SettingItem(
      title: 'Save folder',
      subtitle: 'Folder where conversations are stored.',
      child: _FolderField(
        path: _conversationDir,
        onChanged: (String dir) {
          widget.settings.setConversationDir(dir);
          setState(() => _conversationDir = dir);
        },
      ),
    );
  }

  Widget _buildAutoCleanItem(BuildContext context) {
    return _SettingItem(
      title: 'Auto clean',
      subtitle: 'Automatically delete old conversations.',
      child: Row(
        children: <Widget>[
          Checkbox(
            value: _autoCleanEnabled,
            onChanged: (bool? value) {
              final bool enabled = value ?? false;
              widget.settings.setAutoCleanEnabled(enabled);
              setState(() => _autoCleanEnabled = enabled);
            },
          ),
          const SizedBox(width: 4),
          const Text('Enable automatic cleanup'),
        ],
      ),
    );
  }

  Widget _buildRetentionItem(BuildContext context) {
    return _SettingItem(
      title: 'Delete conversations older than',
      child: Row(
        children: <Widget>[
          SizedBox(
            width: 90,
            child: TextField(
              controller: _retentionController,
              keyboardType: TextInputType.number,
              inputFormatters: <TextInputFormatter>[
                FilteringTextInputFormatter.digitsOnly,
              ],
              decoration: const InputDecoration(
                border: OutlineInputBorder(),
                isDense: true,
              ),
              onChanged: _onRetentionValueChanged,
            ),
          ),
          const SizedBox(width: 12),
          SizedBox(
            width: 140,
            child: DropdownButtonFormField<RetentionUnit>(
              initialValue: _autoCleanUnit,
              decoration: const InputDecoration(
                border: OutlineInputBorder(),
                isDense: true,
              ),
              items: const <DropdownMenuItem<RetentionUnit>>[
                DropdownMenuItem<RetentionUnit>(
                  value: RetentionUnit.days,
                  child: Text('Days'),
                ),
                DropdownMenuItem<RetentionUnit>(
                  value: RetentionUnit.months,
                  child: Text('Months'),
                ),
              ],
              onChanged: (RetentionUnit? unit) {
                if (unit == null) return;
                widget.settings.setAutoCleanUnit(unit);
                setState(() => _autoCleanUnit = unit);
              },
            ),
          ),
        ],
      ),
    );
  }

  void _onRetentionValueChanged(String text) {
    // Fall back to 1 for empty or zero input so retention stays meaningful.
    final int value = int.tryParse(text) ?? 0;
    widget.settings.setAutoCleanValue(value < 1 ? 1 : value);
  }

  Widget _buildModelFolderItem(BuildContext context) {
    return _SettingItem(
      title: 'Model folder',
      subtitle: 'Folder where downloaded models are stored.',
      child: _FolderField(
        path: _modelDir,
        onChanged: (String dir) {
          widget.settings.setModelDir(dir);
          setState(() => _modelDir = dir);
        },
      ),
    );
  }
}


/// A read-only outlined field showing a folder path, with a trailing icon
/// button that opens the native directory picker.
class _FolderField extends StatelessWidget {
  const _FolderField({required this.path, required this.onChanged});

  final String path;
  final ValueChanged<String> onChanged;

  Future<void> _pickFolder() async {
    final String? selected = await FilePicker.getDirectoryPath(
      initialDirectory: path.isEmpty ? null : path,
    );
    if (selected != null) {
      onChanged(selected);
    }
  }

  @override
  Widget build(BuildContext context) {
    return TextField(
      readOnly: true,
      controller: TextEditingController(text: path),
      decoration: InputDecoration(
        border: const OutlineInputBorder(),
        isDense: true,
        suffixIcon: IconButton(
          icon: const Icon(Icons.folder_open),
          tooltip: 'Choose folder',
          onPressed: _pickFolder,
        ),
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
