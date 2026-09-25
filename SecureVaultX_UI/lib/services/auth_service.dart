import '../core/api_client.dart';
import '../core/api_exception.dart';
import '../models/app_user.dart';

/// What the app needs from authentication. Abstract so tests can fake it.
abstract class AuthGateway {
  Future<void> register({required String fullName, required String email, required String password});
  Future<AppUser> login({required String email, required String password});
  Future<void> logout();

  /// The signed-in user, or null when there is no valid session.
  Future<AppUser?> currentUser();
}

class AuthService implements AuthGateway {
  AuthService(this._client);

  final ApiClient _client;

  @override
  Future<void> register({required String fullName, required String email, required String password}) async {
    await _client.postJson('/api/auth/register', {
      'fullName': fullName,
      'email': email,
      'password': password,
    });
  }

  @override
  Future<AppUser> login({required String email, required String password}) async {
    await _client.postForm('/api/auth/login', {'email': email, 'password': password});
    _client.forgetCsrfToken(); // the server rotated the session, therefore the token
    final user = await currentUser();
    if (user == null) throw ApiException(code: 'UNAUTHENTICATED');
    return user;
  }

  @override
  Future<void> logout() async {
    try {
      await _client.postEmpty('/api/auth/logout');
    } finally {
      _client.forgetCsrfToken();
      await _client.clearCookies();
    }
  }

  @override
  Future<AppUser?> currentUser() async {
    try {
      final json = await _client.getJson('/api/auth/me') as Map<String, dynamic>;
      return AppUser.fromJson(json);
    } on ApiException catch (e) {
      if (e.isUnauthenticated) return null;
      rethrow;
    }
  }
}
