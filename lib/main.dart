import 'package:flutter/material.dart';
import 'pages/home_page.dart';
import 'pages/link_page.dart';
import 'pages/model_page.dart';
import 'pages/setting_page.dart';
import 'settings/settings_service.dart';


Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  final SettingsService settings = SettingsService();
  await settings.load();
  runApp(MyApp(settings: settings));
}


/// Delay before tooltips appear on hover. Without this, the chat FAB's tooltip
/// (which hardcodes a zero wait) pops up instantly and feels noisy.
const TooltipThemeData _tooltipTheme = TooltipThemeData(
  waitDuration: Duration(milliseconds: 600),
);


class MyApp extends StatelessWidget {
  const MyApp({super.key, required this.settings});

  final SettingsService settings;

  @override
  Widget build(BuildContext context) {
    return ValueListenableBuilder<Color>(
      valueListenable: settings.seedColor,
      builder: (BuildContext context, Color seed, Widget? child) {
        return MaterialApp(
          title: 'Open Crafter',
          theme: ThemeData(
            colorScheme: ColorScheme.fromSeed(seedColor: seed),
            useMaterial3: true,
            tooltipTheme: _tooltipTheme,
          ),
          darkTheme: ThemeData(
            colorScheme: ColorScheme.fromSeed(
              seedColor: seed,
              brightness: Brightness.dark,
            ),
            useMaterial3: true,
            tooltipTheme: _tooltipTheme,
          ),
          themeMode: ThemeMode.system,
          // Scale all text and icons consistently across every page. The scale
          // is read once at launch, so it only takes effect after a restart.
          builder: (BuildContext context, Widget? child) {
            final MediaQueryData mq = MediaQuery.of(context);
            return MediaQuery(
              data: mq.copyWith(
                textScaler: TextScaler.linear(settings.uiScale),
              ),
              child: IconTheme.merge(
                data: IconThemeData(size: 24.0 * settings.uiScale),
                child: child!,
              ),
            );
          },
          home: HomeShell(settings: settings),
        );
      },
    );
  }
}


class HomeShell extends StatefulWidget {
  const HomeShell({super.key, required this.settings});

  final SettingsService settings;

  @override
  State<HomeShell> createState() => _HomeShellState();
}


class _HomeShellState extends State<HomeShell> {
  int _selectedIndex = 0;

  // Setting is rendered in the rail's `trailing` slot rather than as a
  // destination, so its page index is tracked separately.
  static const int _settingIndex = 3;
  static const int _homeIndex = 0;

  // Lets the chat FAB drive the Home page (start a new conversation) even
  // though the FAB lives here in the parent rail.
  final GlobalKey<HomePageState> _homeKey = GlobalKey<HomePageState>();

  late final List<Widget> _pages = <Widget>[
    HomePage(key: _homeKey, settings: widget.settings),
    const LinkPage(),
    const ModelPage(),
    SettingPage(settings: widget.settings),
  ];

  // Switches to Home (if needed) and asks it to add a new conversation. The
  // call is deferred to a post-frame callback so the Home state exists, and
  // HomePage itself queues the request if it is still loading.
  void _startNewConversation() {
    if (_selectedIndex != _homeIndex) {
      setState(() => _selectedIndex = _homeIndex);
    }
    WidgetsBinding.instance.addPostFrameCallback((_) {
      _homeKey.currentState?.addNewConversation();
    });
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Row(
        children: <Widget>[
          NavigationRail(
            // Tint the rail so it reads as a distinct surface against the page
            // body, rather than blending into the main background.
            backgroundColor: Theme.of(context).colorScheme.surfaceContainer,
            // Setting lives in `trailing` (pinned to the bottom), so it is not
            // part of `destinations`. When Setting is active there is no matching
            // destination, hence the null selectedIndex.
            selectedIndex: _selectedIndex == _settingIndex ? null : _selectedIndex,
            labelType: NavigationRailLabelType.all,
            // Center the destination group in the space between the leading FAB
            // (top) and the trailing Setting button (bottom).
            groupAlignment: 0.0,
            trailingAtBottom: true,
            onDestinationSelected: (int index) {
              setState(() {
                _selectedIndex = index;
              });
            },
            leading: FloatingActionButton(
              tooltip: 'Chat',
              onPressed: _startNewConversation,
              child: const Icon(Icons.chat_outlined),
            ),
            destinations: const <NavigationRailDestination>[
              NavigationRailDestination(
                icon: Icon(Icons.home_outlined),
                selectedIcon: Icon(Icons.home_rounded),
                label: Text('Home'),
              ),
              NavigationRailDestination(
                icon: Icon(Icons.cable_outlined),
                selectedIcon: Icon(Icons.cable),
                label: Text('Link'),
              ),
              NavigationRailDestination(
                icon: Icon(Icons.all_inbox_outlined),
                selectedIcon: Icon(Icons.all_inbox),
                label: Text('Model'),
              ),
            ],
            trailing: Padding(
              padding: const EdgeInsets.only(bottom: 16),
              child: IconButton(
                icon: const Icon(Icons.settings_outlined),
                selectedIcon: const Icon(Icons.settings),
                isSelected: _selectedIndex == _settingIndex,
                tooltip: 'Setting',
                onPressed: () {
                  setState(() {
                    _selectedIndex = _settingIndex;
                  });
                },
              ),
            ),
          ),
          const VerticalDivider(thickness: 1, width: 1),
          Expanded(
            child: IndexedStack(
              index: _selectedIndex,
              children: _pages,
            ),
          ),
        ],
      ),
    );
  }
}
