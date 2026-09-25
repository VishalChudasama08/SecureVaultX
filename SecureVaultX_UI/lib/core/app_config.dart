/// Build-time configuration. Override with:
///   flutter run --dart-define=SVX_API_BASE_URL=http://10.0.2.2:8080   (Android emulator -> host machine)
class AppConfig {
  const AppConfig._();

  static const String apiBaseUrl = String.fromEnvironment(
    'SVX_API_BASE_URL',
    defaultValue: 'http://localhost:8080',
  );

  /// Largest file the phone/desktop app will hand to the platform "save as" API from memory (mobile only).
  static const int mobileInMemorySaveLimitBytes = 64 * 1024 * 1024;
}
