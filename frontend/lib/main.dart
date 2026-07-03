import 'package:flutter/material.dart';
import 'screens/booking_screen.dart';

void main() {
  runApp(const SpaceBookingApp());
}

class SpaceBookingApp extends StatelessWidget {
  const SpaceBookingApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'STELLAR LINES',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF00E5FF),
          brightness: Brightness.dark,
          surface: const Color(0xFF0A0E21),
          primary: const Color(0xFF00E5FF),
          secondary: const Color(0xFFBB86FC),
          tertiary: const Color(0xFF64FFDA),
          error: const Color(0xFFFF5252),
        ),
        scaffoldBackgroundColor: const Color(0xFF0A0E21),
        useMaterial3: true,
        fontFamily: 'Inter',
      ),
      home: const BookingScreen(),
    );
  }
}
