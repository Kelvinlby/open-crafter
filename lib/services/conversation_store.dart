import 'dart:io';

import 'package:path/path.dart' as p;

import '../models/conversation.dart';

/// Reads and writes [ConversationData] JSON files under the conversation
/// directory. Stateless; [dir] is the folder all conversations live in
/// (`SettingsService.conversationDir`).
class ConversationStore {
  const ConversationStore(this.dir);

  final String dir;

  /// Allocates a collision-free `conv_<utc-timestamp>.json` path for a new
  /// conversation. The base name uses millisecond precision; if that file
  /// already exists (e.g. two conversations started in the same millisecond),
  /// `_2`, `_3`, … are appended until a free name is found.
  String allocatePath(DateTime createdAt) {
    final DateTime t = createdAt.toUtc();
    String two(int n) => n.toString().padLeft(2, '0');
    String three(int n) => n.toString().padLeft(3, '0');
    final String base =
        'conv_${t.year}${two(t.month)}${two(t.day)}_${two(t.hour)}${two(t.minute)}${two(t.second)}_${three(t.millisecond)}';

    String candidate = p.join(dir, '$base.json');
    int suffix = 2;
    while (File(candidate).existsSync()) {
      candidate = p.join(dir, '${base}_$suffix.json');
      suffix++;
    }
    return candidate;
  }

  /// Writes [data] to [path], creating the parent directory if needed.
  Future<void> save(String path, ConversationData data) async {
    final File file = File(path);
    await file.parent.create(recursive: true);
    await file.writeAsString(data.toJsonString());
  }

  /// Reads and parses the conversation at [path]. Propagates IO and
  /// [FormatException] errors so the caller can show the error state.
  Future<ConversationData> load(String path) async {
    final String source = await File(path).readAsString();
    return ConversationData.fromJsonString(source);
  }
}

/// Derives a short, single-line conversation title from the first prompt.
/// Collapses whitespace and truncates to ~40 characters with an ellipsis.
String titleFromPrompt(String prompt) {
  final String collapsed = prompt.trim().replaceAll(RegExp(r'\s+'), ' ');
  if (collapsed.length <= 40) return collapsed;
  return '${collapsed.substring(0, 40).trimRight()}…';
}
