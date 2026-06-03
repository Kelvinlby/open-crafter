import 'package:flutter/material.dart';
import 'pages/home_page.dart';
import 'pages/connector_page.dart';
import 'pages/model_page.dart';
import 'pages/setting_page.dart';


void main() {
  runApp(const MyApp());
}


class MyApp extends StatelessWidget {
  const MyApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'Open Crafter',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: Colors.deepPurple),
        useMaterial3: true,
      ),
      darkTheme: ThemeData.dark(),
      themeMode: ThemeMode.system,
      home: const HomeShell(),
    );
  }
}


class HomeShell extends StatefulWidget {
  const HomeShell({super.key});

  @override
  State<HomeShell> createState() => _HomeShellState();
}


class _HomeShellState extends State<HomeShell> {
  int _selectedIndex = 0;

  // Setting is rendered in the rail's `trailing` slot rather than as a
  // destination, so its page index is tracked separately.
  static const int _settingIndex = 3;

  static const List<Widget> _pages = <Widget>[
    HomePage(),
    ConnectorPage(),
    ModelPage(),
    SettingPage(),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: Row(
        children: <Widget>[
          NavigationRail(
            // Setting lives in `trailing` (pinned to the bottom), so it is not
            // part of `destinations`. When Setting is active there is no matching
            // destination, hence the null selectedIndex.
            selectedIndex: _selectedIndex == _settingIndex ? null : _selectedIndex,
            labelType: NavigationRailLabelType.selected,
            onDestinationSelected: (int index) {
              setState(() {
                _selectedIndex = index;
              });
            },
            destinations: const <NavigationRailDestination>[
              NavigationRailDestination(
                icon: Icon(Icons.home),
                label: Text('Home'),
              ),
              NavigationRailDestination(
                icon: Icon(Icons.cable),
                label: Text('Connector'),
              ),
              NavigationRailDestination(
                icon: Icon(Icons.all_inbox),
                label: Text('Model'),
              ),
            ],
            trailing: Expanded(
              child: Align(
                alignment: Alignment.bottomCenter,
                child: Padding(
                  padding: const EdgeInsets.only(bottom: 8),
                  child: IconButton(
                    icon: const Icon(Icons.settings),
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
