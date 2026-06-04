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
    this.updatedAt,
    this.isNew = false,
  });

  /// Path to the backing JSON file, or null for an unsaved [isNew] item.
  final String? filePath;

  /// Conversation title. Null when the JSON omitted it (renders as a disabled
  /// "Missing title" card).
  final String? title;

  /// Last-interaction timestamp from the JSON metadata: set when the
  /// conversation is created and bumped to the current time on every new
  /// message. Drives the card subtitle and the recent-first list order. Null
  /// when missing/unparseable (renders as a disabled "Missing date" card).
  final DateTime? updatedAt;

  /// True for the in-memory item created by the chat FAB before it is saved.
  final bool isNew;

  /// New items are always selectable; loaded items require both fields present.
  bool get isSelectable => isNew || (title != null && updatedAt != null);

  /// An unsaved conversation placeholder created from the chat FAB.
  factory Conversation.newItem() =>
      Conversation(isNew: true, updatedAt: DateTime.now());

  /// Parses a conversation card from a JSON file following the on-disk shape:
  /// `{ "metadata": { "title": ..., "timestamp": ... }, ... }`.
  ///
  /// Missing or unparseable `title`/`timestamp` fields are left null so the
  /// card renders disabled with placeholder text. A file that fails to parse
  /// entirely yields a fully-null (non-selectable) card rather than throwing,
  /// so one bad file does not break the whole list.
  factory Conversation.fromJsonFile(File file) {
    String? title;
    DateTime? updatedAt;
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
            updatedAt = DateTime.tryParse(rawTimestamp);
          }
        }
      }
    } catch (_) {
      // Malformed file: fall through with null fields (disabled card).
    }
    return Conversation(
      filePath: file.path,
      title: title,
      updatedAt: updatedAt,
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


/// Author of a single [Message]. Anything other than `user`/`agent` on disk is
/// treated as [unknown] so a stray role does not crash the loader; the UI then
/// renders it in neither the prompt nor the response slot.
enum MessageRole {
  user,
  agent,
  unknown;

  /// Tolerant parse of the on-disk `role` string.
  static MessageRole parse(Object? raw) {
    switch (raw) {
      case 'user':
        return MessageRole.user;
      case 'agent':
        return MessageRole.agent;
      default:
        return MessageRole.unknown;
    }
  }

  /// On-disk spelling. [unknown] round-trips as the literal `unknown`, which is
  /// only produced if such a value was already present in the file.
  String get wire => name;
}


/// A single turn within a conversation: who said it, the (markdown) text, and
/// when. Mirrors one entry of the on-disk `messages` array.
class Message {
  const Message({required this.role, required this.content, this.timestamp});

  final MessageRole role;

  /// Raw markdown text. Empty when the JSON omitted or blanked `content`; the
  /// UI substitutes a "No prompt"/"No response" chip in that case.
  final String content;

  /// When the turn was created. Null when missing/unparseable.
  final DateTime? timestamp;

  factory Message.fromJson(Map<String, dynamic> json) {
    final dynamic rawContent = json['content'];
    final dynamic rawTimestamp = json['timestamp'];
    return Message(
      role: MessageRole.parse(json['role']),
      content: rawContent is String ? rawContent : '',
      timestamp: rawTimestamp is String ? DateTime.tryParse(rawTimestamp) : null,
    );
  }

  Map<String, dynamic> toJson() => <String, dynamic>{
        'role': role.wire,
        'content': content,
        if (timestamp != null) 'timestamp': timestamp!.toUtc().toIso8601String(),
      };
}


/// One rendered exchange: a user prompt and the agent response to it. Either
/// side may be null when the underlying `messages` list is missing that role
/// (e.g. a prompt still awaiting a reply, or a stray leading agent message).
typedef MessagePair = ({Message? user, Message? agent});


/// The full on-disk payload of a conversation: metadata plus the ordered list
/// of [Message]s.
///
/// Unlike [Conversation] (the card model, which deliberately swallows parse
/// errors so one bad file does not break the list), [ConversationData.fromJsonString]
/// *propagates* malformed-JSON errors so the detail view can surface them.
class ConversationData {
  ConversationData({this.title, this.updatedAt, List<Message>? messages})
      : messages = messages ?? <Message>[];

  String? title;

  /// Last-interaction timestamp: the conversation's creation time, bumped to
  /// the current time on every new message. Serialised as `metadata.timestamp`.
  DateTime? updatedAt;
  final List<Message> messages;

  /// An empty in-memory conversation for a freshly started (unsaved) chat.
  factory ConversationData.empty() => ConversationData();

  /// Parses the on-disk shape:
  /// `{ "metadata": { "title", "timestamp" }, "messages": [ ... ], "content": [] }`.
  ///
  /// Throws [FormatException] if the text is not valid JSON or not the expected
  /// object shape, so the caller can render the error state.
  factory ConversationData.fromJsonString(String source) {
    final dynamic decoded = jsonDecode(source);
    if (decoded is! Map<String, dynamic>) {
      throw const FormatException('Conversation root is not a JSON object.');
    }

    String? title;
    DateTime? updatedAt;
    final dynamic metadata = decoded['metadata'];
    if (metadata is Map<String, dynamic>) {
      final dynamic rawTitle = metadata['title'];
      if (rawTitle is String && rawTitle.isNotEmpty) title = rawTitle;
      final dynamic rawTimestamp = metadata['timestamp'];
      if (rawTimestamp is String) updatedAt = DateTime.tryParse(rawTimestamp);
    }

    final List<Message> messages = <Message>[];
    final dynamic rawMessages = decoded['messages'];
    if (rawMessages is List) {
      for (final dynamic entry in rawMessages) {
        if (entry is Map<String, dynamic>) {
          messages.add(Message.fromJson(entry));
        }
      }
    }

    return ConversationData(
      title: title,
      updatedAt: updatedAt,
      messages: messages,
    );
  }

  /// Serialises back to the on-disk shape. The `content` array is preserved as
  /// an empty list for forward-compatibility (it is unused today).
  String toJsonString() {
    final Map<String, dynamic> root = <String, dynamic>{
      'metadata': <String, dynamic>{
        if (title != null) 'title': title,
        if (updatedAt != null) 'timestamp': updatedAt!.toUtc().toIso8601String(),
      },
      'messages': messages.map((Message m) => m.toJson()).toList(),
      'content': <dynamic>[],
    };
    return const JsonEncoder.withIndent('  ').convert(root);
  }

  /// Groups [messages] into ordered user→agent [MessagePair]s for rendering.
  ///
  /// Each `user` message opens a pair that the next `agent` message closes. A
  /// user with no following agent, or an agent with no preceding open user
  /// (e.g. a stray leading agent), yields a pair with the other side null.
  /// [unknown] roles are skipped.
  List<MessagePair> get pairs {
    final List<MessagePair> result = <MessagePair>[];
    Message? pendingUser;
    for (final Message m in messages) {
      switch (m.role) {
        case MessageRole.user:
          // A previous unanswered prompt becomes its own (agent-less) pair.
          if (pendingUser != null) {
            result.add((user: pendingUser, agent: null));
          }
          pendingUser = m;
        case MessageRole.agent:
          result.add((user: pendingUser, agent: m));
          pendingUser = null;
        case MessageRole.unknown:
          break;
      }
    }
    if (pendingUser != null) {
      result.add((user: pendingUser, agent: null));
    }
    return result;
  }
}
