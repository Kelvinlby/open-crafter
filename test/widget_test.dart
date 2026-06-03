// Basic smoke test for the Open Crafter app shell.

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:shared_preferences/shared_preferences.dart';

import 'package:open_crafter/main.dart';
import 'package:open_crafter/settings/settings_service.dart';

void main() {
  testWidgets('App builds and shows the navigation rail', (WidgetTester tester) async {
    SharedPreferences.setMockInitialValues(<String, Object>{});
    final SettingsService settings = SettingsService();
    await settings.load();

    await tester.pumpWidget(MyApp(settings: settings));
    await tester.pumpAndSettle();

    // The navigation rail and its Setting button should be present.
    expect(find.byType(NavigationRail), findsOneWidget);
    expect(find.byIcon(Icons.settings), findsOneWidget);
  });
}
