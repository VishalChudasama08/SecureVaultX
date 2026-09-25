import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

import '../screens/app_shell.dart';
import '../screens/decrypt_screen.dart';
import '../screens/encrypt_screen.dart';
import '../screens/home_screen.dart';
import '../screens/login_screen.dart';
import '../screens/register_screen.dart';
import '../screens/vault_screen.dart';
import '../state/auth_controller.dart';

class Routes {
  const Routes._();

  static const splash = '/splash';
  static const login = '/login';
  static const register = '/register';
  static const home = '/';
  static const encrypt = '/encrypt';
  static const vault = '/vault';
  static const decrypt = '/decrypt';
}

/// Route guard: signed-out users can only see login/register; signed-in users never see them.
GoRouter buildRouter(AuthController auth) {
  return GoRouter(
    initialLocation: Routes.splash,
    refreshListenable: auth,
    redirect: (context, state) {
      final at = state.matchedLocation;
      final publicPage = at == Routes.login || at == Routes.register;
      switch (auth.status) {
        case AuthStatus.unknown:
          return at == Routes.splash ? null : Routes.splash;
        case AuthStatus.unauthenticated:
          return publicPage ? null : Routes.login;
        case AuthStatus.authenticated:
          return (publicPage || at == Routes.splash) ? Routes.home : null;
      }
    },
    routes: [
      GoRoute(
        path: Routes.splash,
        builder: (context, state) => const Scaffold(body: Center(child: CircularProgressIndicator())),
      ),
      GoRoute(path: Routes.login, builder: (context, state) => const LoginScreen()),
      GoRoute(path: Routes.register, builder: (context, state) => const RegisterScreen()),
      ShellRoute(
        builder: (context, state, child) => AppShell(location: state.matchedLocation, child: child),
        routes: [
          GoRoute(path: Routes.home, builder: (context, state) => const HomeScreen()),
          GoRoute(path: Routes.encrypt, builder: (context, state) => const EncryptScreen()),
          GoRoute(path: Routes.vault, builder: (context, state) => const VaultScreen()),
          GoRoute(path: Routes.decrypt, builder: (context, state) => const DecryptScreen()),
        ],
      ),
    ],
  );
}
