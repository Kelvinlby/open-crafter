import 'dart:convert';
import 'dart:io';


/// A single conversation shown as a card in the Home page list.
///
/// Loaded from a JSON file on disk, or created in-memory as an unsaved "new"
/// item (see [Conversation.newItem]) when the user starts a fresh conversation
/// from the chat FAB. Only the metadata needed to render a card is kept here;
/// the messages/content payload is read lazily elsewhere when a card is opened.
class Conversation {
  const Conversation({
    this.filePath,
    this.title,
    this.createdAt,
    this.isNew = false,
  });

  /// Path to the backing JSON file, or null for an unsaved [isNew] item.
  final String? filePath;

  /// Conversation title. Null when the JSON omitted it (renders as a disabled
  /// "Missing title" card).
  final String? title;

  /// Creation timestamp from the JSON metadata. Null when missing/unparseable
  /// (renders as a disabled "Missing date" card).
  final DateTime? createdAt;

  /// True for the in-memory item created by the chat FAB before it is saved.
  final bool isNew;

  /// New items are always selectable; loaded items require both fields present.
  bool get isSelectable => isNew || (title != null && createdAt != null);

  /// An unsaved conversation placeholder created from the chat FAB.
  factory Conversation.newItem() =>
      Conversation(isNew: true, createdAt: DateTime.now());

  /// Parses a conversation card from a JSON file following the on-disk shape:
  /// `{ "metadata": { "title": ..., "timestamp": ... }, ... }`.
  ///
  /// Missing or unparseable `title`/`timestamp` fields are left null so the
  /// card renders disabled with placeholder text. A file that fails to parse
  /// entirely yields a fully-null (non-selectable) card rather than throwing,
  /// so one bad file does not break the whole list.
  factory Conversation.fromJsonFile(File file) {
    String? title;
    DateTime? createdAt;
    try {
      final dynamic decoded = jsonDecode(file.readAsStringSync());
      if (decoded is Map<String, dynamic>) {
        final dynamic metadata = decoded['metadata'];
        if (metadata is Map<String, dynamic>) {
          final dynamic rawTitle = metadata['title'];
          if (rawTitle is String && rawTitle.isNotEmpty) {
            title = rawTitle;
          }
          final dynamic rawTimestamp = metadata['timestamp'];
          if (rawTimestamp is String) {
            createdAt = DateTime.tryParse(rawTimestamp);
          }
        }
      }
    } catch (_) {
      // Malformed file: fall through with null fields (disabled card).
    }
    return Conversation(
      filePath: file.path,
      title: title,
      createdAt: createdAt,
    );
  }
}


/// Formats a timestamp for a conversation card subtitle as `yyyy-MM-dd HH:mm`.
/// Avoids an `intl` dependency for this single, fixed format.
String formatConversationDate(DateTime date) {
  String two(int n) => n.toString().padLeft(2, '0');
  return '${date.year}-${two(date.month)}-${two(date.day)} '
      '${two(date.hour)}:${two(date.minute)}';
}
