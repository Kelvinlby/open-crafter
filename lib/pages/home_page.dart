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

  /// Reads the conversation directory, ensures it exists, and loads the JSON
  /// files into [_conversations]. Any failure flips to the error state.
  Future<void> _load() async {
    try {
      final String dirPath = widget.settings.conversationDir;
      final Directory dir = Directory(dirPath);
      if (!await dir.exists()) {
        await dir.create(recursive: true);
      }

      final List<Conversation> loaded = <Conversation>[];
      final List<FileSystemEntity> entries = await dir.list().toList();
      for (final FileSystemEntity entity in entries) {
        if (entity is File && entity.path.toLowerCase().endsWith('.json')) {
          loaded.add(Conversation.fromJsonFile(entity));
        }
      }

      // Recent on top; items missing a date sort to the bottom.
      loaded.sort((Conversation a, Conversation b) {
        if (a.createdAt == null && b.createdAt == null) return 0;
        if (a.createdAt == null) return 1;
        if (b.createdAt == null) return -1;
        return b.createdAt!.compareTo(a.createdAt!);
      });

      if (!mounted) return;
      setState(() {
        _conversations
          ..clear()
          ..addAll(loaded);
        _status = _Status.ready;
      });

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

    // Append the user turn and persist (first save point).
    data.messages.add(
      Message(role: MessageRole.user, content: text, timestamp: DateTime.now()),
    );

    final Conversation? selected = _selected;
    String? path = selected?.filePath;
    if (selected != null && (selected.isNew || path == null)) {
      // First send of a brand-new conversation: give it a title/timestamp, a
      // collision-free file, and promote the list entry to a saved card.
      data.title ??= titleFromPrompt(text);
      data.createdAt ??= selected.createdAt ?? DateTime.now();
      path = _store.allocatePath(data.createdAt!);
      _promoteNewItem(selected, filePath: path, data: data);
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
      createdAt: data.createdAt,
    );
    final int index = _conversations.indexOf(item);
    if (index >= 0) _conversations[index] = saved;
    if (identical(_newItem, item)) _newItem = null;
    _selected = saved;
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
    final String subtitleText = c.createdAt != null
        ? formatConversationDate(c.createdAt!)
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

    return SizeTransition(
      sizeFactor: curved,
      alignment: Alignment.topCenter,
      child: FadeTransition(
        opacity: curved,
        child: Card(
          clipBehavior: Clip.antiAlias,
          color: cardColor,
          margin: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
          child: ListTile(
            selected: selected,
            selectedTileColor: Colors.transparent,
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
            onTap: c.isSelectable ? () => _selectConversation(c) : null,
          ),
        ),
      ),
    );
  }

  Widget _buildContent(BuildContext context) {
    return Column(
      children: <Widget>[
        // Top bar with the list toggle.
        Padding(
          padding: const EdgeInsets.all(8),
          child: Align(
            alignment: Alignment.centerLeft,
            child: IconButton(
              tooltip: _listVisible ? 'Hide list' : 'Show list',
              icon: Icon(_listVisible ? Icons.menu_open : Icons.menu),
              onPressed: () => setState(() => _listVisible = !_listVisible),
            ),
          ),
        ),
        // Conversation content area for the selected card. The list fills the
        // whole area down to the window bottom; the composer floats on top of
        // it (the list's bottom padding lets the last message scroll clear of
        // the card).
        Expanded(
          child: Stack(
            children: <Widget>[
              Positioned.fill(child: _buildConversation(context)),
              Positioned(
                left: 0,
                right: 0,
                bottom: 0,
                child: _buildComposer(context),
              ),
            ],
          ),
        ),
      ],
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
      // Bottom inset clears the floating composer so the last message can
      // scroll above it; +8 keeps a small gap between them.
      padding: EdgeInsets.fromLTRB(24, 16, 24, _composerHeight + 8),
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
              child: Padding(
                padding: const EdgeInsets.all(_kComposerPadding),
                child: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: <Widget>[
                    // Multiline input field, with its own 12px inset on all sides.
                    Padding(
                      padding: const EdgeInsets.all(12),
                      child: ConstrainedBox(
                        constraints: BoxConstraints(maxHeight: maxFieldHeight),
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
                        // Nudge the Send button in so its gap to the card's
                        // right edge (12 card padding + 4 = 16) reads the same
                        // as the gap below it.
                        const SizedBox(width: 4),
                      ],
                    ),
                  ],
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
            alpha: (l[2] *
                    focusBoost *
                    (0.55 + 0.45 * _wave(t * l[1] + l[0])))
                .clamp(0.0, 1.0),
          ),
          blurRadius: l[3] * (focused ? 1.5 : 1.0),
          spreadRadius: l[4] * (focused ? 1.5 : 1.0),
        ),
    ];
  }

  /// A 0..1 sine wave for a phase in turns (1.0 == full cycle).
  double _wave(double turns) =>
      0.5 + 0.5 * math.sin(turns * 2 * math.pi);

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
      icon: const Icon(Icons.send, size: 25),
      label: Row(
        mainAxisSize: MainAxisSize.min,
        children: <Widget>[
          const Text('Send', style: TextStyle(fontSize: 15)),
          const SizedBox(width: 8),
          _buildKeycap(context, 'Ctrl'),
          const SizedBox(width: 4),
          _buildKeycap(context, 'Enter'),
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
  /// to sit legibly on the filled button surface.
  Widget _buildKeycap(BuildContext context, String label) {
    final ColorScheme colors = Theme.of(context).colorScheme;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 6.5, vertical: 2.5),
      decoration: BoxDecoration(
        color: colors.onPrimary.withValues(alpha: 0.18),
        borderRadius: BorderRadius.circular(4.5),
      ),
      child: Text(
        label,
        style: TextStyle(
          color: colors.onPrimary,
          fontSize: 12,
          fontWeight: FontWeight.w600,
          height: 1.0,
        ),
      ),
    );
  }
}
