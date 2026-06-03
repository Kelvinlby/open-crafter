import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

import '../models/conversation.dart';
import '../settings/settings_service.dart';


/// Loading lifecycle of the Home page.
enum _Status { loading, error, ready }


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
class HomePageState extends State<HomePage> {
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

  final TextEditingController _composerController = TextEditingController();
  final ScrollController _composerScrollController = ScrollController();

  /// Drives insert/remove animations for the conversation list.
  final GlobalKey<AnimatedListState> _listKey =
      GlobalKey<AnimatedListState>();

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _composerController.dispose();
    _composerScrollController.dispose();
    super.dispose();
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
    // discardNewItemIfNeeded drives its own removal animation (no setState),
    // so only the selection highlight needs a rebuild here.
    discardNewItemIfNeeded(newlySelected: conversation);
    setState(() => _selected = conversation);
  }

  /// Sends the composer text. Triggered by the Send button and Ctrl+Enter.
  /// Wiring to actual message handling is out of scope here.
  void _send() {
    final String text = _composerController.text.trim();
    if (text.isEmpty) return;
    // TODO: dispatch the message; for now just clear the composer.
    _composerController.clear();
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
            Text(
              'Error: $_errorMessage',
              textAlign: TextAlign.center,
            ),
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
        // Conversation content area (selection rendering is out of scope here).
        const Expanded(child: SizedBox.expand()),
        _buildComposer(context),
      ],
    );
  }

  Widget _buildComposer(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.all(24),
      child: LayoutBuilder(
        builder: (BuildContext context, BoxConstraints constraints) {
          // The input may grow with content but never past half the page.
          final double maxFieldHeight = MediaQuery.of(context).size.height / 2;
          return Card(
            elevation: 4,
            clipBehavior: Clip.antiAlias,
            shape: RoundedRectangleBorder(
              borderRadius: BorderRadius.circular(_kComposerRadius),
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
                      child: CallbackShortcuts(
                        bindings: <ShortcutActivator, VoidCallback>{
                          const SingleActivator(
                            LogicalKeyboardKey.enter,
                            control: true,
                          ): _send,
                        },
                        child: TextField(
                          controller: _composerController,
                          scrollController: _composerScrollController,
                          minLines: 1,
                          maxLines: null,
                          keyboardType: TextInputType.multiline,
                          // height: 1.0 strips the font's line leading so the
                          // gap above the first line matches the sides.
                          style: const TextStyle(height: 1.0, fontSize: 16),
                          decoration: const InputDecoration(
                            hintText: 'Type a message...',
                            hintStyle: TextStyle(height: 1.0, fontSize: 16),
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
                          onPressed: () {},
                        ),
                      ),
                      const SizedBox(width: _kToolbarGap),
                      Tooltip(
                        message: 'Options',
                        waitDuration: _kTooltipWait,
                        child: IconButton(
                          icon: const Icon(Icons.tune),
                          onPressed: () {},
                        ),
                      ),
                      // Stretch so the send button is right-aligned.
                      const Spacer(),
                      _buildSendButton(context),
                    ],
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }

  /// Ordinary high-emphasis send button. Sized to the same 48px height as the
  /// add/option icon buttons so its top and bottom align with them — it reads
  /// as a filled, labelled version of those buttons. Shows the Ctrl+Enter
  /// shortcut as keycap chips after the label.
  Widget _buildSendButton(BuildContext context) {
    return FilledButton.icon(
      onPressed: _send,
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
