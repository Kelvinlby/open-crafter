import 'dart:async';
import 'dart:io';
import 'dart:math' as math;

import 'package:file_picker/file_picker.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:path/path.dart' as p;

import '../models/conversation.dart';
import '../services/conversation_store.dart';
import '../services/dummy_model.dart';
import '../settings/settings_service.dart';
import '../widgets/message_pair.dart';

/// Loading lifecycle of the Home page (the list pane).
enum _Status { loading, error, ready }

/// Load lifecycle of the right-hand detail pane for the selected conversation.
/// Independent of [_Status]: the list can be ready while a single conversation
/// is still loading or failed to parse.
enum _DetailStatus { idle, loading, error, ready }

/// Hover delay before tooltips appear, matching the navigation rail buttons
/// (see the global `TooltipThemeData` in main.dart).
const Duration _kTooltipWait = Duration(milliseconds: 600);

/// Uniform inset between the per-card trash button and the card's top, right
/// and bottom edges.
const double _kCardTrashInset = 8;

/// Corner radius of the composer card.
const double _kComposerRadius = 24;

/// Uniform inset on all four sides of the composer card, also reused as the
/// vertical gap between the input field and the toolbar.
const double _kComposerPadding = 12;

/// Horizontal gap between the add and options icon buttons in the toolbar.
const double _kToolbarGap = 4;

/// Standard Material [IconButton] touch-target size; the Send button matches
/// this height so it aligns with the add/option buttons.
const double _kIconButtonSize = 48;

class HomePage extends StatefulWidget {
  const HomePage({super.key, required this.settings});

  final SettingsService settings;

  @override
  State<HomePage> createState() => HomePageState();
}

/// Public so the parent shell can drive it through a [GlobalKey] — the chat FAB
/// lives in the navigation rail and calls [addNewConversation] on this state.
class HomePageState extends State<HomePage>
    with SingleTickerProviderStateMixin {
  _Status _status = _Status.loading;
  String _errorMessage = '';

  /// Conversations shown in the left list, recent first.
  final List<Conversation> _conversations = <Conversation>[];

  /// The currently selected card, if any.
  Conversation? _selected;

  /// The card the mouse is currently over; drives the trash button's fade-in.
  Conversation? _hovered;

  /// The unsaved item created by the FAB, if one is currently at the top.
  Conversation? _newItem;

  /// Whether the left list pane is shown (toggled by the menu button).
  bool _listVisible = true;

  /// Fraction of the page width given to the list pane. Seeded from settings,
  /// updated live while dragging the divider, and persisted on drag end.
  late double _listFraction = widget.settings.convListFraction;

  /// True while the divider is being dragged. Suppresses the pane's resize
  /// animation so the width tracks the cursor 1:1 instead of chasing each
  /// per-frame change (which causes flashing).
  bool _draggingDivider = false;

  /// Identifies the conversation list pane so the delete SnackBar can measure
  /// its on-screen rect and constrain itself to that column.
  final GlobalKey _listPaneKey = GlobalKey();

  /// Set when the FAB is pressed while still loading; honoured once ready.
  bool _addPendingOnReady = false;

  /// Load state of the detail pane for [_selected].
  _DetailStatus _detailStatus = _DetailStatus.idle;
  String _detailError = '';

  /// Messages of the currently selected conversation. Null until a card with a
  /// loaded payload is selected.
  ConversationData? _activeData;

  /// True while the dummy model is streaming a reply; gates the composer.
  bool _streaming = false;

  /// Cumulative text of the in-flight agent reply, rendered as the trailing
  /// open pair while [_streaming].
  String _streamingText = '';

  /// Subscription to the active inference stream, cancelled on dispose.
  StreamSubscription<String>? _streamSub;

  final TextEditingController _composerController = TextEditingController();
  final ScrollController _composerScrollController = ScrollController();

  /// Focus of the composer text field. Drives the card's glowing outline, which
  /// brightens while the field is focused (à la Google AI Studio).
  final FocusNode _composerFocus = FocusNode();

  /// Free-running clock for the composer's "breathing" glow. Each glow layer
  /// reads it at a different phase/speed so the pulse shimmers rather than
  /// throbbing in unison. Created in [initState], disposed in [dispose].
  late final AnimationController _glowController;

  /// Absolute paths of files attached to the next message via the Add button.
  /// Passed to the model on send, then cleared. Not persisted to history.
  final List<String> _attachments = <String>[];

  /// Measures the floating composer so the message list can reserve matching
  /// bottom padding, letting the last message scroll clear above it.
  final GlobalKey _composerKey = GlobalKey();

  /// Latest measured composer height; seeds the list's bottom inset. Updated
  /// after each layout via [_measureComposer].
  double _composerHeight = 0;

  /// Measures the floating top toolbar so the message list can reserve matching
  /// top padding, letting the first message scroll clear below it.
  final GlobalKey _toolbarKey = GlobalKey();

  /// Latest measured toolbar height; seeds the list's top inset. Updated after
  /// each layout via [_measureToolbar].
  double _topInset = 0;

  /// Model options shown in the toolbar's "Model" dropdown, and the current
  /// selection.
  ///
  /// TODO(model-loading): Replace the dummy seed with the real catalog of
  /// installed/available models. Expected integration:
  ///   * Source the list from the Model page's backing store — the same data
  ///     `ModelPage` (lib/pages/model_page.dart) will list — likely via a shared
  ///     service (e.g. a `ModelService`/`ModelStore`) injected into this page,
  ///     not a local list. Each entry should carry an id + display name (replace
  ///     `String` with a `ModelInfo` type) so selection survives renames.
  ///   * Load asynchronously in [initState] (or listen to the service) and
  ///     `setState` when it resolves; show a loading/empty state in the dropdown
  ///     while the catalog is being fetched.
  ///   * Persist `_selectedModel` in `SettingsService` so the last-used model is
  ///     restored on launch instead of starting null.
  final List<String> _models = <String>['Model A', 'Model B'];
  String? _selectedModel;

  /// Link options shown in the toolbar's "Link" dropdown, and the current
  /// selection.
  ///
  /// TODO(link-loading): Replace the dummy seed with real connection/link
  /// targets. Expected integration:
  ///   * Source from the Link page's backing data (lib/pages/link_page.dart) via
  ///     a shared `LinkService`, mirroring the model dropdown. Entries should be
  ///     a `LinkInfo` (id + label + reachable/health flag), not bare strings.
  ///   * A link can change state at runtime (break/freeze/reconnect); subscribe
  ///     to the service and `setState` on change so the dropdown and the
  ///     collapsed pill's status reflect it live.
  ///   * Persist `_selectedLink` in `SettingsService` like `_selectedModel`.
  final List<String> _links = <String>['Link 1', 'Link 2'];
  String? _selectedLink;

  /// Whether the model is "loaded" (running). Drives the load/pause toggle in
  /// the toolbar.
  ///
  /// TODO(model-loading): Currently a visual-only flag toggled synchronously.
  /// Real integration:
  ///   * Loading is async and can fail — replace this bool with a small state
  ///     enum (`unloaded`/`loading`/`loaded`/`error`) so the toggle can show a
  ///     spinner while loading and an error affordance on failure.
  ///   * `_modelLoaded` (or the enum) should be derived from the runtime/backend
  ///     load state, not owned here, so it stays correct if the model is
  ///     unloaded elsewhere or the process dies. See [_toggleModelLoaded].
  bool _modelLoaded = false;

  /// True while the mouse is over the toolbar; expands the collapsed pill.
  bool _toolbarHovered = false;

  /// True while the toolbar (or one of its dropdowns) holds focus; keeps the
  /// pill expanded so an open dropdown menu doesn't collapse it mid-selection.
  bool _toolbarFocused = false;

  /// True while a toolbar dropdown's menu is open. The menu is a separate modal
  /// route whose barrier steals hover and whose scope steals focus, so without
  /// this latch the capsule would collapse out from under the open menu (and
  /// unmount it). Set when a dropdown is tapped open ([_buildToolbarDropdown]);
  /// cleared on selection ([_onModelSelected]/[_onLinkSelected]) or when the
  /// pointer re-enters the capsule after the menu closes ([_buildCapsule]).
  bool _dropdownOpen = false;

  /// Fraction of the model's context window currently used (0..1). Shown in the
  /// collapsed pill.
  ///
  /// TODO(runtime-status): Replace the hardcoded 0.6 with live runtime info.
  /// Expected integration:
  ///   * Compute from the active conversation's token count against the loaded
  ///     model's context window (tokens-used / context-size). The token count
  ///     should come from the inference backend that replaces `dummyInference`
  ///     (lib/services/dummy_model.dart), updated as messages stream in.
  ///   * Make it non-final and `setState` (or drive it from a stream) as usage
  ///     changes; it resets when the conversation or model changes.
  ///   * Consider widening this into a small status object (context %, tokens/s,
  ///     link health) so [_statusLabel] can show richer state without more
  ///     fields. See [_statusLabel].
  final double _contextUsed = 0.6;

  /// The toolbar shows as a compact pill until something forces it open: a
  /// missing Model/Link choice or an unloaded model (the cases where the user
  /// needs the controls), or while hovered/focused.
  bool get _toolbarExpanded =>
      _toolbarHovered ||
      _toolbarFocused ||
      _dropdownOpen ||
      _selectedModel == null ||
      _selectedLink == null ||
      !_modelLoaded;

  /// Scrolls the conversation list so new/streamed messages stay in view.
  final ScrollController _conversationScrollController = ScrollController();

  /// Persists conversations under the configured directory.
  late final ConversationStore _store = ConversationStore(
    widget.settings.conversationDir,
  );

  /// Drives insert/remove animations for the conversation list.
  final GlobalKey<AnimatedListState> _listKey = GlobalKey<AnimatedListState>();

  @override
  void initState() {
    super.initState();
    // Repaint the composer's glow whenever focus enters/leaves the field.
    _composerFocus.addListener(_onComposerFocusChanged);
    // Long, slow cycle; the glow layers sample it at different phases so the
    // breathing never lines up into a single uniform pulse.
    _glowController = AnimationController(
      vsync: this,
      duration: const Duration(seconds: 6),
    )..repeat();
    _load();
  }

  @override
  void dispose() {
    _streamSub?.cancel();
    _composerController.dispose();
    _composerScrollController.dispose();
    _composerFocus.dispose();
    _glowController.dispose();
    _conversationScrollController.dispose();
    super.dispose();
  }

  /// Rebuilds so the composer's glowing outline can animate with focus.
  void _onComposerFocusChanged() {
    if (mounted) setState(() {});
  }

  /// Reads the floating composer's rendered height after layout and, if it
  /// changed, stores it so the message list can reserve matching bottom
  /// padding. Scheduled as a post-frame callback because the size is only known
  /// once the composer has been laid out.
  void _measureComposer() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final double? height = _composerKey.currentContext?.size?.height;
      if (height != null && (height - _composerHeight).abs() > 0.5) {
        setState(() => _composerHeight = height);
      }
    });
  }

  /// Reads the floating toolbar's rendered height after layout and reserves it
  /// as the message list's top padding. Mirrors [_measureComposer]; the size is
  /// only known once laid out.
  ///
  /// Crucially this only ever *grows* [_topInset], latching the toolbar's
  /// tallest (expanded) height. The capsule animates its height with
  /// [AnimatedSize], so a measurement taken mid-collapse/expand reads a
  /// transient in-between value; accepting those would tug the conversation list
  /// up and down by a few pixels each time the pointer crosses the toolbar.
  /// Reserving the max means the list's inset stays constant and the first
  /// message clears the toolbar in either state, so the list never shifts.
  void _measureToolbar() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      final double? height = _toolbarKey.currentContext?.size?.height;
      if (height != null && height > _topInset + 0.5) {
        setState(() => _topInset = height);
      }
    });
  }

  /// Reads the conversation directory, ensures it exists, and loads the JSON
  /// files into [_conversations]. Any failure flips to the error state.
  Future<void> _load() async {
    try {
      final String dirPath = widget.settings.conversationDir;
      final Directory dir = Directory(dirPath);
      if (!await dir.exists()) {
        await dir.create(recursive: true);
      }

      // When auto-clean is on, conversations created before this instant are
      // deleted on entry rather than listed. Null when the feature is off.
      final DateTime? cutoff = widget.settings.autoCleanCutoff();

      final List<Conversation> loaded = <Conversation>[];
      final List<FileSystemEntity> entries = await dir.list().toList();
      for (final FileSystemEntity entity in entries) {
        if (entity is File && entity.path.toLowerCase().endsWith('.json')) {
          final Conversation conv = Conversation.fromJsonFile(entity);
          // Files with no parseable date are kept; we only drop ones we can
          // confirm are older than the cutoff.
          if (cutoff != null &&
              conv.updatedAt != null &&
              conv.updatedAt!.isBefore(cutoff)) {
            try {
              await _store.delete(entity.path);
              continue;
            } catch (_) {
              // A failed deletion shouldn't break loading; just list the file.
            }
          }
          loaded.add(conv);
        }
      }

      // Recent on top; items missing a date sort to the bottom.
      loaded.sort((Conversation a, Conversation b) {
        if (a.updatedAt == null && b.updatedAt == null) return 0;
        if (a.updatedAt == null) return 1;
        if (b.updatedAt == null) return -1;
        return b.updatedAt!.compareTo(a.updatedAt!);
      });

      if (!mounted) return;

      // Default-select the most recent (top) selectable card so the detail
      // pane shows a conversation on entry instead of the empty placeholder.
      // Items missing a title/date sort to the bottom and are skipped.
      Conversation? initial;
      for (final Conversation c in loaded) {
        if (c.isSelectable) {
          initial = c;
          break;
        }
      }

      setState(() {
        _conversations
          ..clear()
          ..addAll(loaded);
        _status = _Status.ready;
        _selected = initial;
      });
      if (initial != null) {
        _loadDetail(initial);
      }

      if (_addPendingOnReady) {
        _addPendingOnReady = false;
        addNewConversation();
      }
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _errorMessage = e.toString();
        _status = _Status.error;
      });
    }
  }

  /// Inserts an unsaved conversation at the top of the list and selects it.
  ///
  /// Safe to call at any time: while loading the request is deferred until the
  /// page is ready; on error it is a no-op. If a new item already exists, it is
  /// simply re-selected rather than duplicated. Called by the chat FAB.
  void addNewConversation() {
    if (_status == _Status.loading) {
      _addPendingOnReady = true;
      return;
    }
    if (_status == _Status.error) return;

    if (_newItem != null) {
      setState(() => _selected = _newItem);
      _loadDetail(_newItem!);
      _focusComposer();
      return;
    }
    final Conversation item = Conversation.newItem();
    _newItem = item;
    _conversations.insert(0, item);
    _selected = item;
    // Animate the new card sliding in; fall back to a plain rebuild if the
    // list isn't mounted yet (e.g. same frame we became ready).
    final AnimatedListState? list = _listKey.currentState;
    if (list != null) {
      list.insertItem(0, duration: const Duration(milliseconds: 250));
      setState(() {});
    } else {
      setState(() {});
    }
    _loadDetail(item);
    _focusComposer();
  }

  /// Moves keyboard focus to the composer so the user can type straight away
  /// after the chat FAB creates/selects a new conversation. Deferred to the
  /// next frame because the composer may be (re)built this frame.
  void _focusComposer() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _composerFocus.requestFocus();
    });
  }

  /// Removes the unsaved new item unless [newlySelected] *is* that item.
  ///
  /// Exposed so other code can decide whether the pending new item survives a
  /// selection change. Returns true if an item was discarded.
  bool discardNewItemIfNeeded({required Conversation newlySelected}) {
    final Conversation? pending = _newItem;
    if (pending == null || identical(pending, newlySelected)) return false;
    final int index = _conversations.indexOf(pending);
    _newItem = null;
    if (index < 0) return true;
    _conversations.removeAt(index);
    // Animate the card collapsing/fading out; the cards below slide up to
    // fill the gap. The removed card is rebuilt one last time for the exit.
    final AnimatedListState? list = _listKey.currentState;
    if (list != null) {
      list.removeItem(
        index,
        (BuildContext context, Animation<double> animation) =>
            _buildCard(context, pending, animation),
        duration: const Duration(milliseconds: 250),
      );
    }
    return true;
  }

  void _selectConversation(Conversation conversation) {
    if (identical(conversation, _selected)) return;
    // discardNewItemIfNeeded drives its own removal animation (no setState),
    // so only the selection highlight needs a rebuild here.
    discardNewItemIfNeeded(newlySelected: conversation);
    setState(() => _selected = conversation);
    _loadDetail(conversation);
  }

  /// Deletes [c] from the list: animates the card out, re-selects a neighbour if
  /// it was the selected card, and (for a saved card) removes its JSON file —
  /// deferred behind an Undo SnackBar so the deletion can be reversed.
  Future<void> _deleteConversation(Conversation c) async {
    final int index = _conversations.indexOf(c);
    if (index < 0) return;

    final bool wasSelected = identical(c, _selected);

    // Pick the replacement selection before the list shrinks: the card just
    // above (more recent) if any, else the new top — skipping disabled cards.
    final Conversation? target = wasSelected
        ? _nextSelectionAfterRemoving(index)
        : null;

    if (_hovered == c) _hovered = null;
    if (identical(_newItem, c)) _newItem = null;
    _conversations.removeAt(index);

    // Animate the card collapsing/fading out; cards below slide up to fill in.
    final AnimatedListState? list = _listKey.currentState;
    if (list != null) {
      list.removeItem(
        index,
        (BuildContext context, Animation<double> animation) =>
            _buildCard(context, c, animation),
        duration: const Duration(milliseconds: 250),
      );
    }

    if (wasSelected) {
      setState(() => _selected = target);
      if (target != null) {
        _loadDetail(target);
      } else {
        _resetDetailToIdle();
      }
    } else {
      setState(() {}); // Refresh the (now empty?) list state.
    }

    // Unsaved new items have no file on disk, so nothing more to do (and no
    // Undo affordance — the chat FAB recreates them trivially).
    final String? path = c.filePath;
    if (c.isNew || path == null) return;

    // Defer the disk delete until the Undo SnackBar closes; Undo restores the
    // card and selection without ever touching disk.
    final ScaffoldMessengerState messenger = ScaffoldMessenger.of(context);
    final ColorScheme colors = Theme.of(context).colorScheme;
    messenger.clearSnackBars();
    bool undone = false;
    final ScaffoldFeatureController<SnackBar, SnackBarClosedReason> controller =
        messenger.showSnackBar(
          SnackBar(
            behavior: SnackBarBehavior.floating,
            margin: _listPaneMargin(),
            // Material 3's default neutral SnackBar surface: a high-contrast bar
            // (dark in light mode, light in dark mode) that's calm rather than
            // alarming. These roles come from the active ColorScheme, so it
            // follows the light/dark mode and seed color.
            backgroundColor: colors.inverseSurface,
            // SnackBars with an action default to persist: true, which keeps
            // them on screen forever. Force auto-dismiss after the duration.
            persist: false,
            duration: const Duration(seconds: 4),
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(8),
            ),
            content: Text(
              'Deleted "${c.title ?? 'conversation'}"',
              style: TextStyle(color: colors.onInverseSurface),
            ),
            action: SnackBarAction(
              label: 'Undo',
              textColor: colors.inversePrimary,
              onPressed: () {
                undone = true;
                _restoreConversation(c, index, wasSelected);
              },
            ),
          ),
        );
    final SnackBarClosedReason reason = await controller.closed;
    if (!undone && reason != SnackBarClosedReason.action) {
      await _store.delete(path);
    }
  }

  /// Constrains the floating delete SnackBar to the conversation list column so
  /// it doesn't span the whole window. Measures the list pane's on-screen rect
  /// (relative to the Scaffold, which fills the window) and turns it into a
  /// horizontal margin. Falls back to a default 8px margin when the pane can't
  /// be measured (e.g. collapsed), keeping the SnackBar usable either way.
  EdgeInsets _listPaneMargin() {
    const EdgeInsets fallback = EdgeInsets.all(8);
    final RenderObject? box = _listPaneKey.currentContext?.findRenderObject();
    if (box is! RenderBox || !box.hasSize || box.size.width < 80) {
      return fallback;
    }
    final double left = box.localToGlobal(Offset.zero).dx;
    final double screenWidth = MediaQuery.of(context).size.width;
    final double right = (screenWidth - left - box.size.width).clamp(
      0.0,
      screenWidth,
    );
    return EdgeInsets.only(left: left + 8, right: right + 8, bottom: 8);
  }

  /// Returns the card that should become selected after removing the card at
  /// [index]: the nearest selectable card above it (more recent), else the
  /// nearest selectable card below it, or null when none remain.
  Conversation? _nextSelectionAfterRemoving(int index) {
    for (int i = index - 1; i >= 0; i--) {
      if (_conversations[i].isSelectable) return _conversations[i];
    }
    for (int i = index + 1; i < _conversations.length; i++) {
      if (_conversations[i].isSelectable) return _conversations[i];
    }
    return null;
  }

  /// Re-inserts a card removed by [_deleteConversation] (Undo): slides it back
  /// into its original slot and restores selection if it had been selected.
  void _restoreConversation(Conversation c, int index, bool wasSelected) {
    if (!mounted) return;
    final int idx = index.clamp(0, _conversations.length);
    _conversations.insert(idx, c);
    final AnimatedListState? list = _listKey.currentState;
    if (list != null) {
      list.insertItem(idx, duration: const Duration(milliseconds: 250));
    }
    if (wasSelected) {
      setState(() => _selected = c);
      _loadDetail(c);
    } else {
      setState(() {});
    }
  }

  /// Returns the detail pane to its empty placeholder, cancelling any in-flight
  /// stream. Used when the deleted card was selected and nothing remains to
  /// select.
  void _resetDetailToIdle() {
    _streamSub?.cancel();
    _streamSub = null;
    setState(() {
      _activeData = null;
      _detailStatus = _DetailStatus.idle;
      _streaming = false;
      _streamingText = '';
    });
  }

  /// Loads the messages for [conversation] into the detail pane.
  ///
  /// The unsaved new item starts as an empty (ready) chat. A saved card is read
  /// from disk; a parse/IO failure flips the pane to the error state showing the
  /// "⚠" panel rather than crashing the list.
  Future<void> _loadDetail(Conversation conversation) async {
    // Any selection change ends an in-flight stream for the previous chat.
    _streamSub?.cancel();
    _streamSub = null;

    if (conversation.isNew || conversation.filePath == null) {
      setState(() {
        _activeData = ConversationData.empty();
        _detailStatus = _DetailStatus.ready;
        _streaming = false;
        _streamingText = '';
      });
      return;
    }

    final String path = conversation.filePath!;
    setState(() {
      _detailStatus = _DetailStatus.loading;
      _streaming = false;
      _streamingText = '';
      _activeData = null;
    });

    try {
      final ConversationData data = await _store.load(path);
      // Guard against a newer selection having superseded this load.
      if (!mounted || !identical(_selected, conversation)) return;
      setState(() {
        _activeData = data;
        _detailStatus = _DetailStatus.ready;
      });
    } catch (e) {
      if (!mounted || !identical(_selected, conversation)) return;
      setState(() {
        _detailError = e.toString();
        _detailStatus = _DetailStatus.error;
      });
    }
  }

  /// Sends the composer text: appends the user prompt, persists, then streams a
  /// dummy reply that is appended and persisted again when complete.
  ///
  /// Triggered by the Send button and Ctrl+Enter. Ignored while a reply is
  /// streaming (the composer is also visually disabled then).
  Future<void> _send() async {
    if (_streaming) return;
    final String text = _composerController.text.trim();
    if (text.isEmpty) return;

    // Snapshot the attachments for this send before the list is cleared below.
    final List<String> files = List<String>.of(_attachments);

    final ConversationData data = _activeData ??= ConversationData.empty();

    // One interaction time, shared by the user turn and the conversation's
    // last-interaction timestamp.
    final DateTime now = DateTime.now();

    // Append the user turn and persist (first save point).
    data.messages.add(
      Message(role: MessageRole.user, content: text, timestamp: now),
    );

    final Conversation? selected = _selected;
    String? path = selected?.filePath;
    if (selected != null && (selected.isNew || path == null)) {
      // First send of a brand-new conversation: give it a title, stamp it with
      // the interaction time, allocate a collision-free file, and promote the
      // list entry to a saved card.
      data.title ??= titleFromPrompt(text);
      data.updatedAt = now;
      path = _store.allocatePath(now);
      _promoteNewItem(selected, filePath: path, data: data);
    } else if (selected != null) {
      // Continuing an existing conversation: bump its timestamp to the
      // interaction time and float the card to the top of the list.
      data.updatedAt = now;
      _bumpToTop(selected, now);
    }

    _composerController.clear();
    setState(() {
      _streaming = true;
      _streamingText = '';
      _attachments.clear();
    });
    if (path != null) {
      await _store.save(path, data);
    }
    _scrollToBottom();

    // Stream the dummy reply, rendering cumulative text as it arrives.
    _streamSub = dummyInference(text, files).listen(
      (String chunk) {
        if (!mounted) return;
        setState(() => _streamingText = chunk);
        _scrollToBottom();
      },
      onDone: () async {
        if (!mounted) return;
        final String reply = _streamingText;
        data.messages.add(
          Message(
            role: MessageRole.agent,
            content: reply,
            timestamp: DateTime.now(),
          ),
        );
        setState(() {
          _streaming = false;
          _streamingText = '';
        });
        if (path != null) {
          await _store.save(path, data); // Second save point.
        }
        _scrollToBottom();
      },
      cancelOnError: true,
    );
  }

  /// Replaces the unsaved new-item card with an equivalent saved [Conversation]
  /// (so it is no longer discarded on selection change) and clears [_newItem].
  void _promoteNewItem(
    Conversation item, {
    required String filePath,
    required ConversationData data,
  }) {
    final Conversation saved = Conversation(
      filePath: filePath,
      title: data.title,
      updatedAt: data.updatedAt,
    );
    final int index = _conversations.indexOf(item);
    if (index >= 0) _conversations[index] = saved;
    if (identical(_newItem, item)) _newItem = null;
    _selected = saved;
  }

  /// Replaces [old] with a copy timestamped [when] and floats it to the top of
  /// the list, sliding the card up from its old slot, and re-selects the
  /// relocated card. A card already at the top is just replaced in place.
  ///
  /// Drives its own backing-list mutation but no [setState] — the caller's
  /// subsequent rebuild repaints the list with the bumped subtitle/order.
  void _bumpToTop(Conversation old, DateTime when) {
    final int index = _conversations.indexOf(old);
    if (index < 0) return;
    final Conversation bumped = Conversation(
      filePath: old.filePath,
      title: old.title,
      updatedAt: when,
    );
    _conversations.removeAt(index);
    _conversations.insert(0, bumped);
    _selected = bumped;
    if (identical(_hovered, old)) _hovered = bumped;
    final AnimatedListState? list = _listKey.currentState;
    if (list != null && index != 0) {
      list.removeItem(
        index,
        (BuildContext context, Animation<double> animation) =>
            _buildCard(context, old, animation),
        duration: const Duration(milliseconds: 250),
      );
      list.insertItem(0, duration: const Duration(milliseconds: 250));
    }
  }

  /// Scrolls the conversation view to the bottom after the next frame so newly
  /// added or streamed content stays in view.
  void _scrollToBottom() {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!_conversationScrollController.hasClients) return;
      _conversationScrollController.animateTo(
        _conversationScrollController.position.maxScrollExtent,
        duration: const Duration(milliseconds: 250),
        curve: Curves.easeOut,
      );
    });
  }

  @override
  Widget build(BuildContext context) {
    switch (_status) {
      case _Status.loading:
        return _buildLoading();
      case _Status.error:
        return _buildError();
      case _Status.ready:
        return _buildReady(context);
    }
  }

  Widget _buildLoading() {
    return const Center(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          CircularProgressIndicator(),
          SizedBox(height: 16),
          Text('Loading...'),
        ],
      ),
    );
  }

  Widget _buildError() {
    final ColorScheme colors = Theme.of(context).colorScheme;
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            Icon(Icons.warning_amber_rounded, size: 64, color: colors.error),
            const SizedBox(height: 16),
            Text('Error: $_errorMessage', textAlign: TextAlign.center),
          ],
        ),
      ),
    );
  }

  Widget _buildReady(BuildContext context) {
    return LayoutBuilder(
      builder: (BuildContext context, BoxConstraints constraints) {
        final double totalWidth = constraints.maxWidth;
        final double listWidth = _listVisible
            ? (totalWidth * _listFraction).clamp(
                SettingsService.minConvListFraction * totalWidth,
                SettingsService.maxConvListFraction * totalWidth,
              )
            : 0;

        return Row(
          children: <Widget>[
            // The conversation list pane collapses to zero width when toggled
            // off, while the parent navigation rail stays visible regardless.
            // The toggle is animated, but the animation is suppressed during a
            // divider drag so the width follows the cursor without flashing.
            AnimatedSize(
              duration: _draggingDivider
                  ? Duration.zero
                  : const Duration(milliseconds: 200),
              curve: Curves.easeInOut,
              child: SizedBox(
                key: _listPaneKey,
                width: listWidth,
                child: _listVisible
                    ? _buildList(context)
                    : const SizedBox.shrink(),
              ),
            ),
            if (_listVisible) _buildDivider(totalWidth),
            Expanded(child: _buildContent(context)),
          ],
        );
      },
    );
  }

  /// A draggable divider that resizes the list pane. The fraction is updated
  /// live during the drag and persisted when the drag ends. A centered slim
  /// rounded bar hints that the divider can be dragged.
  Widget _buildDivider(double totalWidth) {
    final ColorScheme colors = Theme.of(context).colorScheme;
    return MouseRegion(
      cursor: SystemMouseCursors.resizeLeftRight,
      child: GestureDetector(
        behavior: HitTestBehavior.translucent,
        onHorizontalDragStart: (DragStartDetails details) {
          setState(() => _draggingDivider = true);
        },
        onHorizontalDragUpdate: (DragUpdateDetails details) {
          if (totalWidth <= 0) return;
          setState(() {
            _listFraction = (_listFraction + details.delta.dx / totalWidth)
                .clamp(
                  SettingsService.minConvListFraction,
                  SettingsService.maxConvListFraction,
                );
          });
        },
        onHorizontalDragEnd: (DragEndDetails details) {
          setState(() => _draggingDivider = false);
          widget.settings.setConvListFraction(_listFraction);
        },
        // Widen the hit area around the thin visual line for easier grabbing.
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 3),
          child: Stack(
            alignment: Alignment.center,
            children: <Widget>[
              const VerticalDivider(thickness: 1, width: 1),
              Container(
                width: 4,
                height: 32,
                decoration: BoxDecoration(
                  color: colors.onSurfaceVariant,
                  borderRadius: BorderRadius.circular(2),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildList(BuildContext context) {
    if (_conversations.isEmpty) {
      return const Center(
        child: Padding(
          padding: EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: <Widget>[
              Icon(Icons.chat, size: 48),
              SizedBox(height: 16),
              Text(
                'Click the chat button on top-left to start new conversation',
                textAlign: TextAlign.center,
              ),
            ],
          ),
        ),
      );
    }

    return AnimatedList(
      key: _listKey,
      padding: const EdgeInsets.symmetric(vertical: 8),
      initialItemCount: _conversations.length,
      itemBuilder:
          (BuildContext context, int index, Animation<double> animation) =>
              _buildCard(context, _conversations[index], animation),
    );
  }

  /// Builds a single conversation card. The [animation] drives the insert/exit
  /// transition (size collapse + fade) so adding pushes the others down and
  /// removing lets the others slide up.
  Widget _buildCard(
    BuildContext context,
    Conversation c,
    Animation<double> animation,
  ) {
    final ColorScheme colors = Theme.of(context).colorScheme;
    final bool selected = identical(c, _selected);
    final bool disabled = !c.isSelectable;
    final String titleText = c.isNew
        ? 'New conversation'
        : (c.title ?? 'No title');
    final String subtitleText = c.updatedAt != null
        ? formatConversationDate(c.updatedAt!)
        : 'No date';

    // Selected cards get a clear themed tint; disabled (non-clickable) cards
    // are dimmed in both plate and text so they recede from the list.
    final Color? cardColor = selected
        ? colors.secondaryContainer
        : disabled
        ? colors.surfaceContainerHighest.withValues(alpha: 0.4)
        : null;
    final Color? titleColor = selected
        ? colors.onSecondaryContainer
        : disabled
        ? colors.onSurface.withValues(alpha: 0.38)
        : null;
    final Color? subtitleColor = disabled
        ? colors.onSurface.withValues(alpha: 0.38)
        : selected
        ? colors.onSecondaryContainer
        : null;

    final CurvedAnimation curved = CurvedAnimation(
      parent: animation,
      curve: Curves.easeInOut,
    );

    // The trash button is revealed only while the mouse is over this card; the
    // slot is always reserved so showing/hiding it never reflows the title.
    final bool showDelete = identical(_hovered, c);

    return SizeTransition(
      sizeFactor: curved,
      alignment: Alignment.topCenter,
      child: FadeTransition(
        opacity: curved,
        child: MouseRegion(
          onEnter: (_) => setState(() => _hovered = c),
          onExit: (_) {
            if (identical(_hovered, c)) setState(() => _hovered = null);
          },
          child: Card(
            clipBehavior: Clip.antiAlias,
            color: cardColor,
            margin: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
            child: ListTile(
              selected: selected,
              selectedTileColor: Colors.transparent,
              // Drop the tile's default right/vertical content padding so the
              // trailing button's own uniform inset (below) is the only gap to
              // the card edges, keeping it equal on top, right and bottom.
              contentPadding: const EdgeInsets.only(left: 16),
              title: Text(
                titleText,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: TextStyle(color: titleColor),
              ),
              subtitle: Text(
                subtitleText,
                style: TextStyle(color: subtitleColor),
              ),
              // The uniform padding makes this the tallest cell, so ListTile
              // centres it flush and the inset reads equally on all sides.
              trailing: Padding(
                padding: const EdgeInsets.all(_kCardTrashInset),
                child: AnimatedOpacity(
                  opacity: showDelete ? 1 : 0,
                  duration: const Duration(milliseconds: 120),
                  child: IgnorePointer(
                    ignoring: !showDelete,
                    child: IconButton(
                      icon: const Icon(Icons.delete_outline),
                      tooltip: 'Delete conversation',
                      onPressed: () => _deleteConversation(c),
                      padding: EdgeInsets.zero,
                      constraints: const BoxConstraints.tightFor(
                        width: 40,
                        height: 40,
                      ),
                    ),
                  ),
                ),
              ),
              onTap: c.isSelectable ? () => _selectConversation(c) : null,
            ),
          ),
        ),
      ),
    );
  }

  Widget _buildContent(BuildContext context) {
    // The conversation list fills the whole pane up to the top edge; the
    // hamburger, toolbar and composer all float over it. The list reserves top
    // and bottom padding so its first/last messages scroll clear of the
    // floating controls.
    return Stack(
      children: <Widget>[
        Positioned.fill(child: _buildConversation(context)),
        // Floating Material 3 toolbar, centered across the top. The list-toggle
        // lives at its left edge.
        Positioned(
          top: 8,
          left: 0,
          right: 0,
          child: Align(
            alignment: Alignment.topCenter,
            child: _buildToolbar(context),
          ),
        ),
        Positioned(
          left: 0,
          right: 0,
          bottom: 0,
          child: _buildComposer(context),
        ),
      ],
    );
  }

  /// The floating Material 3 toolbar: a capsule-shaped, elevated bar that
  /// collapses to a compact pill (model name + context indicator) during normal
  /// conversation, and expands to the full control set — list-toggle, the Model
  /// and Link dropdowns, and the load/restart buttons — on hover/focus or while
  /// a selection is missing or the model is unloaded (see [_toolbarExpanded]).
  ///
  /// The list-toggle lives inside the capsule when expanded; when the capsule
  /// shrinks it "drips out" into a standalone floating circle to the left, the
  /// same height as and vertically centered with the shrunk capsule.
  Widget _buildToolbar(BuildContext context) {
    // Re-measure each build so the list's top inset tracks the toolbar height
    // (which scales with the global text scale).
    _measureToolbar();
    final Widget capsule = _buildCapsule(context);
    if (_toolbarExpanded) return capsule;
    // Collapsed: the hamburger sits beside the capsule as its own circle.
    // IntrinsicHeight + stretch sizes it to the capsule's height; AspectRatio
    // keeps it round; CrossAxisAlignment defaults to centre via the stretch.
    return IntrinsicHeight(
      child: Row(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: <Widget>[
          AspectRatio(
            aspectRatio: 1,
            child: _buildStandaloneHamburger(context),
          ),
          const SizedBox(width: 8),
          capsule,
        ],
      ),
    );
  }

  /// The capsule itself (without the dripped-out hamburger): the hover/focus
  /// region, elevated stadium surface, and the animated collapsed/expanded swap.
  Widget _buildCapsule(BuildContext context) {
    final ColorScheme colors = Theme.of(context).colorScheme;
    return MouseRegion(
      // Entering the capsule also clears the dropdown latch: while a menu is
      // open its barrier covers the capsule, so onEnter can only fire once the
      // menu has closed and the pointer is back over the bar.
      onEnter: (_) => setState(() {
        _toolbarHovered = true;
        _dropdownOpen = false;
      }),
      onExit: (_) => setState(() => _toolbarHovered = false),
      child: Focus(
        // Keep the bar expanded while any descendant holds focus. We do NOT
        // clear the dropdown latch here: DropdownButton focuses its field
        // *before* opening the menu (dropdown.dart _handleTap), so a focus-gain
        // fires at open time — clearing the latch then would collapse the
        // capsule out from under the just-opened menu. The latch is cleared on
        // selection or on pointer re-entry instead.
        onFocusChange: (bool focused) =>
            setState(() => _toolbarFocused = focused),
        child: Material(
          key: _toolbarKey,
          color: colors.surfaceContainer,
          elevation: 4,
          shape: const StadiumBorder(),
          clipBehavior: Clip.antiAlias,
          child: AnimatedSize(
            duration: const Duration(milliseconds: 200),
            curve: Curves.easeInOut,
            alignment: Alignment.topCenter,
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
              child: _toolbarExpanded
                  ? _buildToolbarExpanded(context)
                  : _buildToolbarCollapsed(context),
            ),
          ),
        ),
      ),
    );
  }

  /// The list-toggle as it appears inside the expanded toolbar.
  Widget _buildListToggle() {
    return IconButton(
      tooltip: _listVisible ? 'Hide list' : 'Show list',
      icon: Icon(_listVisible ? Icons.menu_open : Icons.menu),
      onPressed: () => setState(() => _listVisible = !_listVisible),
    );
  }

  /// The list-toggle as a standalone floating circle, shown only while the
  /// capsule is collapsed. Sized by its parent (a stretched, square cell) to
  /// match the capsule height; the icon is centred and its tap target loosened
  /// so it never overflows a short pill.
  Widget _buildStandaloneHamburger(BuildContext context) {
    final ColorScheme colors = Theme.of(context).colorScheme;
    return Material(
      color: colors.surfaceContainer,
      elevation: 4,
      shape: const CircleBorder(),
      clipBehavior: Clip.antiAlias,
      child: Center(
        child: IconButton(
          tooltip: _listVisible ? 'Hide list' : 'Show list',
          icon: Icon(_listVisible ? Icons.menu_open : Icons.menu),
          onPressed: () => setState(() => _listVisible = !_listVisible),
          padding: EdgeInsets.zero,
          constraints: const BoxConstraints(),
        ),
      ),
    );
  }

  /// The collapsed pill: the active model name and a small circular context
  /// indicator to its right. Only reachable once a model is selected and loaded,
  /// so the model name is always present here.
  Widget _buildToolbarCollapsed(BuildContext context) {
    final TextTheme text = Theme.of(context).textTheme;
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: <Widget>[
        const SizedBox(width: 4),
        Text(
          _selectedModel ?? 'No model',
          style: text.titleSmall,
        ),
        const SizedBox(width: 10),
        _buildContextIndicator(context),
        const SizedBox(width: 4),
      ],
    );
  }

  /// A small determinate ring showing context-window usage, sitting to the right
  /// of the model name in the collapsed pill. The exact percentage is exposed on
  /// hover via [_statusLabel].
  ///
  /// TODO(runtime-status): [_contextUsed] is dummy; once it is driven by live
  /// runtime data (see its field doc) this ring reflects real usage with no
  /// change here. Consider tinting it (e.g. toward the error color) as it nears
  /// full.
  Widget _buildContextIndicator(BuildContext context) {
    return Tooltip(
      message: _statusLabel(),
      waitDuration: _kTooltipWait,
      child: SizedBox(
        width: 18,
        height: 18,
        child: CircularProgressIndicator(
          value: _contextUsed,
          strokeWidth: 2.5,
        ),
      ),
    );
  }

  /// The context-usage percentage, shown as the context ring's hover tooltip.
  ///
  /// TODO(runtime-status): Currently formats the dummy [_contextUsed]. Once that
  /// field is backed by real runtime data, this needs no change; extend the
  /// string here (e.g. add link health) if richer status is wanted.
  String _statusLabel() => '${(_contextUsed * 100).round()}% context used';

  /// The full control set, shown when the toolbar is expanded.
  Widget _buildToolbarExpanded(BuildContext context) {
    final ColorScheme colors = Theme.of(context).colorScheme;
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: <Widget>[
        _buildListToggle(),
        const SizedBox(width: 16),
        _buildToolbarDropdown(
          label: 'Model',
          options: _models,
          value: _selectedModel,
          onChanged: _onModelSelected,
        ),
        const SizedBox(width: 12),
        _buildToolbarDropdown(
          label: 'Link',
          options: _links,
          value: _selectedLink,
          onChanged: _onLinkSelected,
        ),
        const SizedBox(width: 16),
        // Load-model toggle: a play button that flips to a pause button. When
        // loaded it fills with the emphasized primary color in a rounded-corner
        // square; idle it sits on a subtle tonal surface. Disabled until a model
        // is chosen — there is nothing to load otherwise.
        IconButton(
          tooltip: _modelLoaded ? 'Unload model' : 'Load model',
          icon: Icon(_modelLoaded ? Icons.pause : Icons.play_arrow),
          onPressed: _selectedModel == null ? null : _toggleModelLoaded,
          style: IconButton.styleFrom(
            foregroundColor: _modelLoaded
                ? colors.onPrimary
                : colors.onSurfaceVariant,
            backgroundColor: _modelLoaded
                ? colors.primary
                : colors.surfaceContainerHighest,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(12),
            ),
          ),
        ),
        const SizedBox(width: 8),
        IconButton(
          tooltip: 'Restart',
          icon: const Icon(Icons.restart_alt),
          onPressed: _restartLink,
        ),
      ],
    );
  }

  /// Handles a Model dropdown selection.
  ///
  /// TODO(model-loading): Currently just records the choice. Expected behavior:
  ///   * Switching the model while one is loaded should unload the old model and
  ///     reset [_modelLoaded] (the user must re-load), so the toggle and pill
  ///     never claim a stale model is running. Optionally auto-load the new one.
  ///   * Persist the choice via `SettingsService` so it is restored next launch.
  ///   * Validate against the live catalog (the selection may have been removed
  ///     since the menu opened).
  void _onModelSelected(String? value) {
    setState(() {
      _selectedModel = value;
      _dropdownOpen = false;
    });
  }

  /// Handles a Link dropdown selection.
  ///
  /// TODO(link-loading): Currently just records the choice. Expected behavior:
  ///   * Establish/switch the connection to the selected link; reflect its
  ///     reachability in the UI and, on failure, force the toolbar open with an
  ///     error state.
  ///   * A loaded model may be tied to a link — decide whether switching links
  ///     requires unloading/reloading, and update [_modelLoaded] accordingly.
  ///   * Persist via `SettingsService`.
  void _onLinkSelected(String? value) {
    setState(() {
      _selectedLink = value;
      _dropdownOpen = false;
    });
  }

  /// Toggles the load/unload state of the selected model.
  ///
  /// TODO(model-loading): Currently flips a local bool synchronously. Expected
  /// behavior:
  ///   * Load: call the runtime/backend to load [_selectedModel] over
  ///     [_selectedLink]. This is async and can fail — drive the load-state
  ///     enum (see [_modelLoaded]) through loading → loaded/error, show a
  ///     spinner on the button while loading, and surface errors.
  ///   * Unload: tear down the loaded model and free its resources.
  ///   * Guard against acting with no model/link selected (shouldn't be
  ///     reachable since the toolbar force-expands and the button could be
  ///     disabled in that state).
  void _toggleModelLoaded() {
    setState(() => _modelLoaded = !_modelLoaded);
  }

  /// Restarts the active link/connection.
  ///
  /// TODO(link-loading): No-op placeholder. Expected behavior:
  ///   * Tear down and re-establish the connection to [_selectedLink] — the
  ///     recovery path for a broken/frozen link.
  ///   * If a model was loaded, decide whether a restart unloads it (likely) and
  ///     update [_modelLoaded]; show progress/error feedback while restarting.
  ///   * Consider confirming if a restart would interrupt an in-flight reply
  ///     ([_streaming]).
  void _restartLink() {}

  /// One outlined-with-label dropdown selector for the toolbar, following the
  /// outlined `DropdownButtonFormField` pattern used in `setting_page.dart`.
  Widget _buildToolbarDropdown({
    required String label,
    required List<String> options,
    required String? value,
    required ValueChanged<String?> onChanged,
  }) {
    return SizedBox(
      width: 180,
      child: DropdownButtonFormField<String>(
        initialValue: value,
        isDense: true,
        // Latch the toolbar open the instant the menu opens, before the menu's
        // barrier/route can strip hover and focus and collapse the capsule.
        onTap: () => setState(() => _dropdownOpen = true),
        decoration: InputDecoration(
          labelText: label,
          border: const OutlineInputBorder(),
          isDense: true,
        ),
        items: <DropdownMenuItem<String>>[
          for (final String option in options)
            DropdownMenuItem<String>(value: option, child: Text(option)),
        ],
        onChanged: onChanged,
      ),
    );
  }

  /// Renders the selected conversation, switching on the detail load state.
  Widget _buildConversation(BuildContext context) {
    switch (_detailStatus) {
      case _DetailStatus.idle:
        return Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Text(
              'Select a conversation, or start a new one.',
              textAlign: TextAlign.center,
              style: TextStyle(
                color: Theme.of(context).colorScheme.onSurfaceVariant,
              ),
            ),
          ),
        );
      case _DetailStatus.loading:
        return const Center(child: CircularProgressIndicator());
      case _DetailStatus.error:
        return _buildDetailError(context);
      case _DetailStatus.ready:
        return _buildMessages(context);
    }
  }

  /// The "⚠" panel shown when a conversation file fails to load/parse.
  Widget _buildDetailError(BuildContext context) {
    final ColorScheme colors = Theme.of(context).colorScheme;
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: <Widget>[
            Icon(Icons.warning_amber_rounded, size: 64, color: colors.error),
            const SizedBox(height: 16),
            Text(
              'Could not load this conversation.',
              style: Theme.of(context).textTheme.titleMedium,
              textAlign: TextAlign.center,
            ),
            const SizedBox(height: 8),
            Text(
              _detailError,
              textAlign: TextAlign.center,
              style: TextStyle(color: colors.onSurfaceVariant),
            ),
          ],
        ),
      ),
    );
  }

  /// The scrollable list of user→agent pairs. While streaming, an extra
  /// trailing pair shows the in-flight reply (the user prompt for it is already
  /// the last message in [_activeData]).
  Widget _buildMessages(BuildContext context) {
    final ConversationData? data = _activeData;
    final List<MessagePair> pairs = data?.pairs ?? const <MessagePair>[];

    if (pairs.isEmpty && !_streaming) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Text(
            'No messages yet. Type below to begin.',
            textAlign: TextAlign.center,
            style: TextStyle(
              color: Theme.of(context).colorScheme.onSurfaceVariant,
            ),
          ),
        ),
      );
    }

    // The last pair is "open" (agent still streaming) when its user has no
    // agent yet and a stream is running; render that one with live text.
    final int count = pairs.length;
    return ListView.separated(
      controller: _conversationScrollController,
      // Top inset clears the floating toolbar and bottom inset clears the
      // floating composer, so the first/last messages scroll clear of them;
      // +8 keeps a small gap on each side.
      padding: EdgeInsets.fromLTRB(24, _topInset + 8, 24, _composerHeight + 8),
      itemCount: count,
      separatorBuilder: (BuildContext context, int index) =>
          const SizedBox(height: 16),
      itemBuilder: (BuildContext context, int index) {
        final MessagePair pair = pairs[index];
        final bool isLast = index == count - 1;
        final bool open = _streaming && isLast && pair.agent == null;
        return MessagePairCard(
          user: pair.user,
          agent: pair.agent,
          streaming: open,
          streamingText: open ? _streamingText : null,
        );
      },
    );
  }

  Widget _buildComposer(BuildContext context) {
    // Re-measure the composer's height each build so the list's bottom inset
    // tracks the card as it grows/shrinks with content.
    _measureComposer();
    return Padding(
      key: _composerKey,
      padding: const EdgeInsets.all(24),
      child: LayoutBuilder(
        builder: (BuildContext context, BoxConstraints constraints) {
          // The input may grow with content but never past half the page.
          final double maxFieldHeight = MediaQuery.of(context).size.height / 2;
          final ColorScheme colors = Theme.of(context).colorScheme;
          final bool focused = _composerFocus.hasFocus;
          // The glow breathes continuously (driven by _glowController) and
          // brightens when focused; the layers pulse out of phase so it
          // shimmers rather than throbbing as one. Tinted with the theme's
          // primary colour so it tracks the seed colour.
          return AnimatedBuilder(
            animation: _glowController,
            child: Card(
              elevation: 4,
              clipBehavior: Clip.antiAlias,
              // Drop Card's default 4px margin so the composer's outer edges
              // line up with the conversation cards (which use zero margin).
              margin: EdgeInsets.zero,
              shape: RoundedRectangleBorder(
                borderRadius: BorderRadius.circular(_kComposerRadius),
                // A subtle primary-tinted border that strengthens on focus,
                // giving the glow a crisp edge like AI Studio's.
                side: BorderSide(
                  color: colors.primary.withValues(alpha: focused ? 0.9 : 0.3),
                  width: focused ? 3 : 2,
                ),
              ),
              // One uniform inset on all four sides wraps the whole composer.
              // Tapping anywhere on the card's empty space focuses the input;
              // the buttons, chips and field win the gesture arena for their
              // own areas, so only blank regions fall through to here.
              // translucent lets taps on the transparent padding register.
              child: GestureDetector(
                behavior: HitTestBehavior.translucent,
                onTap: _composerFocus.requestFocus,
                child: Padding(
                  padding: const EdgeInsets.all(_kComposerPadding),
                  child: Column(
                    mainAxisSize: MainAxisSize.min,
                    children: <Widget>[
                      // Multiline input field, with its own 12px inset on all sides.
                      Padding(
                        padding: const EdgeInsets.all(12),
                        child: ConstrainedBox(
                          constraints: BoxConstraints(
                            maxHeight: maxFieldHeight,
                          ),
                          // Ctrl+Enter sends; plain Enter still inserts a newline.
                          // Ctrl+W drops the first pending attachment.
                          child: CallbackShortcuts(
                            bindings: <ShortcutActivator, VoidCallback>{
                              const SingleActivator(
                                LogicalKeyboardKey.enter,
                                control: true,
                              ): _send,
                              const SingleActivator(
                                LogicalKeyboardKey.keyW,
                                control: true,
                              ): _removeFirstAttachment,
                            },
                            child: TextField(
                              controller: _composerController,
                              focusNode: _composerFocus,
                              scrollController: _composerScrollController,
                              // Input is ignored while a reply streams in.
                              enabled: !_streaming,
                              minLines: 1,
                              maxLines: null,
                              keyboardType: TextInputType.multiline,
                              // height: 1.0 strips the font's line leading so the
                              // gap above the first line matches the sides.
                              style: const TextStyle(height: 1.0, fontSize: 16),
                              decoration: InputDecoration(
                                hintText: _streaming
                                    ? 'Generating response…'
                                    : 'Type a message...',
                                hintStyle: const TextStyle(
                                  height: 1.0,
                                  fontSize: 16,
                                ),
                                border: InputBorder.none,
                                isCollapsed: true,
                                contentPadding: EdgeInsets.zero,
                              ),
                            ),
                          ),
                        ),
                      ),
                      // Vertical gap between the field and the toolbar.
                      const SizedBox(height: _kComposerPadding),
                      // Toolbar: add/options on the left, Send pushed to the right.
                      Row(
                        children: <Widget>[
                          Tooltip(
                            message: 'Add',
                            waitDuration: _kTooltipWait,
                            // IconButton already pads the icon on all four sides.
                            child: IconButton(
                              icon: const Icon(Icons.add),
                              onPressed: _streaming ? null : _pickAttachment,
                            ),
                          ),
                          // Attachment chips fill the leftover width and scroll
                          // horizontally when they overflow; the Expanded also keeps
                          // the send button right-aligned (acting as the old Spacer
                          // when no files are attached).
                          Expanded(
                            child: SingleChildScrollView(
                              scrollDirection: Axis.horizontal,
                              child: Row(
                                children: <Widget>[
                                  const SizedBox(width: _kToolbarGap),
                                  for (final String path
                                      in _attachments) ...<Widget>[
                                    _buildAttachmentChip(path),
                                    const SizedBox(width: _kToolbarGap),
                                  ],
                                ],
                              ),
                            ),
                          ),
                          _buildSendButton(context),
                        ],
                      ),
                    ],
                  ),
                ),
              ),
            ),
            builder: (BuildContext context, Widget? child) {
              return DecoratedBox(
                decoration: BoxDecoration(
                  borderRadius: BorderRadius.circular(_kComposerRadius),
                  boxShadow: _glowShadows(colors.primary, focused),
                ),
                child: child,
              );
            },
          );
        },
      ),
    );
  }

  /// Builds the composer's breathing glow as several primary-tinted shadow
  /// layers. Each layer samples [_glowController] at its own phase and speed,
  /// so their pulses drift in and out of sync — the composite glow shimmers
  /// instead of throbbing uniformly. [focused] lifts the overall intensity.
  List<BoxShadow> _glowShadows(Color tint, bool focused) {
    final double t = _glowController.value; // 0..1, looping
    // Per-layer: (phase offset, cycles-per-loop, base alpha, blur, spread).
    // Different cycle counts mean the layers never realign into one pulse.
    const List<List<double>> layers = <List<double>>[
      <double>[0.0, 1, 0.24, 16, 0.5],
      <double>[0.37, 2, 0.16, 24, 1.5],
      <double>[0.71, 3, 0.11, 12, 0.0],
    ];
    final double focusBoost = focused ? 1.4 : 1.0;
    return <BoxShadow>[
      for (final List<double> l in layers)
        BoxShadow(
          // sin → 0..1, offset so layers breathe independently.
          color: tint.withValues(
            alpha: (l[2] * focusBoost * (0.55 + 0.45 * _wave(t * l[1] + l[0])))
                .clamp(0.0, 1.0),
          ),
          blurRadius: l[3] * (focused ? 1.5 : 1.0),
          spreadRadius: l[4] * (focused ? 1.5 : 1.0),
        ),
    ];
  }

  /// A 0..1 sine wave for a phase in turns (1.0 == full cycle).
  double _wave(double turns) => 0.5 + 0.5 * math.sin(turns * 2 * math.pi);

  /// Ordinary high-emphasis send button. Sized to the same 48px height as the
  /// add/option icon buttons so its top and bottom align with them — it reads
  /// as a filled, labelled version of those buttons. Shows the Ctrl+Enter
  /// shortcut as keycap chips after the label.
  Widget _buildSendButton(BuildContext context) {
    return FilledButton.icon(
      onPressed: _streaming ? null : _send,
      style: FilledButton.styleFrom(
        // Match the IconButton's 48x48 touch target height; shrinkWrap stops
        // Material from adding its own extra vertical tap padding on top.
        minimumSize: const Size(0, _kIconButtonSize),
        tapTargetSize: MaterialTapTargetSize.shrinkWrap,
        // FilledButton.icon defaults to asymmetric (smaller leading, larger
        // trailing) padding; make it symmetric so the icon-to-edge gap matches
        // the chip-to-edge gap.
        padding: const EdgeInsets.symmetric(horizontal: 16),
      ),
      icon: const Icon(Icons.send_rounded, size: 25),
      label: Row(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          const Text('Send', style: TextStyle(fontSize: 15)),
          const SizedBox(width: 8),
          _buildKeycap(context, 'Ctrl', enabled: !_streaming),
          const SizedBox(width: 4),
          _buildKeycap(context, 'Enter', enabled: !_streaming),
        ],
      ),
    );
  }

  /// Opens a native file picker and records the chosen file as an attachment
  /// for the next message. Mirrors the folder picker in `setting_page.dart`.
  /// Any single file is accepted; folders are not selectable. Duplicates and
  /// cancellation are ignored.
  Future<void> _pickAttachment() async {
    final FilePickerResult? result = await FilePicker.pickFiles();
    final String? path = result?.files.single.path;
    if (path == null) return;
    if (_attachments.contains(path)) return;
    setState(() => _attachments.add(path));
  }

  /// Removes the first (oldest) pending attachment, if any. Bound to Ctrl+W
  /// while the composer is focused. No-op when nothing is attached.
  void _removeFirstAttachment() {
    if (_attachments.isEmpty) return;
    setState(() => _attachments.removeAt(0));
  }

  /// A removable chip for one attached file: a file-type icon, the file name,
  /// and an "X" that drops it from the pending attachments.
  Widget _buildAttachmentChip(String path) {
    return Chip(
      visualDensity: VisualDensity.compact,
      avatar: Icon(_iconForFile(path), size: 18),
      label: Text(p.basename(path)),
      onDeleted: () => setState(() => _attachments.remove(path)),
    );
  }

  /// Maps a file's extension to a representative Material icon, falling back to
  /// a generic document icon for anything unrecognised.
  IconData _iconForFile(String path) {
    final String ext = p.extension(path).toLowerCase();
    switch (ext) {
      case '.png':
      case '.jpg':
      case '.jpeg':
      case '.gif':
      case '.webp':
      case '.bmp':
      case '.svg':
        return Icons.image;
      case '.mp3':
      case '.wav':
      case '.flac':
      case '.aac':
      case '.ogg':
        return Icons.audiotrack;
      case '.mp4':
      case '.mov':
      case '.avi':
      case '.mkv':
      case '.webm':
        return Icons.videocam;
      case '.dart':
      case '.js':
      case '.ts':
      case '.py':
      case '.java':
      case '.c':
      case '.cpp':
      case '.h':
      case '.go':
      case '.rs':
      case '.json':
      case '.yaml':
      case '.yml':
      case '.html':
      case '.css':
        return Icons.code;
      case '.txt':
      case '.md':
      case '.pdf':
      case '.doc':
      case '.docx':
        return Icons.description;
      default:
        return Icons.insert_drive_file;
    }
  }

  /// A small keycap-style chip used to render a keyboard shortcut hint, tinted
  /// to sit legibly on the filled button surface. When [enabled] is false it
  /// switches to Material's disabled foreground colour so it dims in step with
  /// the Send button's label and icon.
  Widget _buildKeycap(
    BuildContext context,
    String label, {
    bool enabled = true,
  }) {
    final ColorScheme colors = Theme.of(context).colorScheme;
    // Enabled keycaps sit on the filled (primary) surface, so they tint with
    // onPrimary; disabled ones sit on Material's disabled surface, which uses
    // onSurface @ 0.38 for foreground — match that so they read as dimmed too.
    final Color foreground = enabled
        ? colors.onPrimary
        : colors.onSurface.withValues(alpha: 0.38);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6.5, vertical: 2.5),
      decoration: BoxDecoration(
        color: foreground.withValues(alpha: 0.18),
        borderRadius: BorderRadius.circular(4.5),
      ),
      child: Text(
        label,
        style: TextStyle(
          color: foreground,
          fontSize: 12,
          fontWeight: FontWeight.w600,
          height: 1.0,
        ),
      ),
    );
  }
}
