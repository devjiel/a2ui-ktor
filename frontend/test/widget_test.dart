// Basic smoke test for the A2UI app
import 'package:flutter_test/flutter_test.dart';

import 'package:a2ui_app/main.dart';

void main() {
  testWidgets('App renders without crashing', (WidgetTester tester) async {
    await tester.pumpWidget(const SpaceBookingApp());
    expect(find.text('STELLAR LINES'), findsWidgets);
  });
}
