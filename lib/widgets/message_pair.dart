import 'package:flutter/material.dart';

import '../models/conversation.dart';
import 'markdown_view.dart';

/// One user→agent exchange rendered as a stacked, full-width card (design
/// adapted from Verse): the prompt sits in a tinted inner card on top, the
/// response fills the card body below it.
///
/// A missing/empty side shows a "No prompt"/"No response" chip instead of text.
/// When [streaming] is true and [agent] is null, the response slot shows a
/// progress indicator (before the first chunk) or the live [streamingText].
class MessagePairCard extends StatelessWidget {
  const MessagePairCard({
    super.key,
    required this.user,
    required this.agent,
    this.streaming = false,
    this.streamingText,
  });

  final Message? user;
  final Message? agent;

  /// True for the trailing, still-generating pair; drives the response slot to
  /// render [streamingText] (or a spinner) rather than a "No response" chip.
  final bool streaming;
  final String? streamingText;

  @override
  Widget build(BuildContext context) {
    final ColorScheme colors = Theme.of(context).colorScheme;
    return Card.outlined(
      clipBehavior: Clip.antiAlias,
      margin: EdgeInsets.zero,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: <Widget>[
          // Prompt: tinted inner card.
          Container(
            width: double.infinity,
            color: colors.primaryContainer,
            padding: const EdgeInsets.all(16),
            child: _slot(
              context,
              text: user?.content,
              emptyLabel: 'No prompt',
            ),
          ),
          // While the response is still generating (the stream has not closed),
          // an indeterminate bar sits between the prompt and the response. It
          // keeps animating through network stalls — the response is "done"
          // only when the stream ends, not when text stops arriving.
          if (streaming) const LinearProgressIndicator(),
          // Response: inherits the outlined card surface. Skipped entirely while
          // streaming with no text yet, so the card is just prompt + bar.
          if (!(streaming && (streamingText == null || streamingText!.isEmpty)))
            Padding(
              padding: const EdgeInsets.all(16),
              child: _response(context),
            ),
        ],
      ),
    );
  }

  Widget _response(BuildContext context) {
    // The empty-while-streaming case is handled by the caller (the response
    // block is skipped), so here streaming always has text to show.
    if (streaming) {
      return MarkdownView(streamingText ?? '');
    }
    return _slot(context, text: agent?.content, emptyLabel: 'No response');
  }

  /// Renders [text] as markdown, or an [emptyLabel] chip when it is
  /// missing/blank.
  Widget _slot(
    BuildContext context, {
    required String? text,
    required String emptyLabel,
  }) {
    if (text == null || text.trim().isEmpty) {
      return Align(
        alignment: Alignment.centerLeft,
        child: Chip(
          label: Text(emptyLabel),
          visualDensity: VisualDensity.compact,
        ),
      );
    }
    return MarkdownView(text);
  }
}
