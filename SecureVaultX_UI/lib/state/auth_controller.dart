import 'package:flutter/foundation.dart';

import '../core/api_exception.dart';
import '../models/app_user.dart';
import '../services/auth_service.dart';

enum AuthStatus { unknown, unauthenticated, authenticated }

/// Holds "who is signed in". The router listens to it and redirects; screens call login/register/logout and
/// show the [ApiException] they throw.
class AuthController extends ChangeNotifier {
  AuthController(this._gateway);

  final AuthGateway _gateway;

  AuthStatus _status = AuthStatus.unknown;
  AppUser? _user;

  AuthStatus get status => _status;
  AppUser? get user => _user;

  /// Called once at start-up: is there still a valid server session (persisted cookie)?
  Future<void> bootstrap() async {
    try {
      _user = await _gateway.currentUser();
    } on ApiException {
      _user = null; // server unreachable: show the login screen, where the error will be explained
    }
    _status = _user == null ? AuthStatus.unauthenticated : AuthStatus.authenticated;
    notifyListeners();
  }

  Future<void> login(String email, String password) async {
    final user = await _gateway.login(email: email.trim(), password: password);
    _user = user;
    _status = AuthStatus.authenticated;
    notifyListeners();
  }

  /// Creates the account and signs the new user in.
  Future<void> register(String fullName, String email, String password) async {
    await _gateway.register(fullName: fullName.trim(), email: email.trim(), password: password);
    await login(email, password);
  }

  Future<void> logout() async {
    try {
      await _gateway.logout();
    } on ApiException {
      // Even if the server could not be told, the local session is dropped (cookies are cleared).
    }
    _clear();
  }

  /// The server answered 401 to an authenticated request: the session is gone.
  void sessionExpired() {
    if (_status == AuthStatus.authenticated) _clear();
  }

  void _clear() {
    _user = null;
    _status = AuthStatus.unauthenticated;
    notifyListeners();
  }
}
