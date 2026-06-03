import 'package:flutter/material.dart';
import 'package:flutter_markdown_plus/flutter_markdown_plus.dart';

/// Renders a markdown string with sizing derived from the app [TextTheme].
///
/// A single shared widget is used for both the streaming (in-flight) response
/// and saved messages so the text never visibly re-styles as a stream settles.
/// Styling starts from [MarkdownStyleSheet.fromTheme] — which already binds
/// headings/body/code to the theme's `headlineSmall`/`titleLarge`/`bodyMedium`
/// etc. — and only overrides the few colors that should track the color scheme
/// rather than the hard-coded defaults.
class MarkdownView extends StatelessWidget {
  const MarkdownView(this.data, {super.key});

  /// The raw markdown text. May be partial while a response is streaming.
  final String data;

  @override
  Widget build(BuildContext context) {
    final ThemeData theme = Theme.of(context);
    final ColorScheme colors = theme.colorScheme;

    final MarkdownStyleSheet style =
        MarkdownStyleSheet.fromTheme(theme).copyWith(
      a: TextStyle(
        color: colors.primary,
        decoration: TextDecoration.underline,
      ),
      code: theme.textTheme.bodyMedium!.copyWith(
        fontFamily: 'monospace',
        fontSize: (theme.textTheme.bodyMedium!.fontSize ?? 14) * 0.9,
        backgroundColor: colors.surfaceContainerHighest,
      ),
      codeblockDecoration: BoxDecoration(
        color: colors.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(8),
      ),
      tableBorder: TableBorder.all(color: colors.outlineVariant),
    );

    return MarkdownBody(
      data: data,
      selectable: true,
      styleSheet: style,
    );
  }
}
