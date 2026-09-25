import 'package:flutter/material.dart';

/// Deliberately minimal: only long-stable ThemeData parameters are used, so the app builds on any recent
/// Flutter release (component-theme classes such as CardTheme/InputDecorationTheme were renamed over time).
class AppTheme {
  const AppTheme._();

  static const Color _seed = Color(0xFF1F5FA8);

  static ThemeData light() => _build(Brightness.light);

  static ThemeData dark() => _build(Brightness.dark);

  static ThemeData _build(Brightness brightness) {
    return ThemeData(
      useMaterial3: true,
      colorScheme: ColorScheme.fromSeed(seedColor: _seed, brightness: brightness),
      visualDensity: VisualDensity.adaptivePlatformDensity,
      snackBarTheme: const SnackBarThemeData(behavior: SnackBarBehavior.floating),
    );
  }
}

/// Shared text-field look (outlined), used by every form.
InputDecoration fieldDecoration(String label, {Widget? suffix, String? helper}) => InputDecoration(
      labelText: label,
      helperText: helper,
      suffixIcon: suffix,
      border: const OutlineInputBorder(),
    );
