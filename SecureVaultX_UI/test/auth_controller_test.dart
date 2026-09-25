import 'package:flutter_test/flutter_test.dart';
import 'package:securevaultx_ui/core/api_exception.dart';
import 'package:securevaultx_ui/state/auth_controller.dart';

import 'fakes.dart';

void main() {
  test('bootstrap with a valid persisted session signs the user in', () async {
    final auth = AuthController(FakeAuthGateway(sessionUser: testUser()));
    expect(auth.status, AuthStatus.unknown);
    await auth.bootstrap();
    expect(auth.status, AuthStatus.authenticated);
    expect(auth.user?.email, 'test@example.com');
  });

  test('bootstrap without a session, or with the server down, shows the login state', () async {
    final none = AuthController(FakeAuthGateway());
    await none.bootstrap();
    expect(none.status, AuthStatus.unauthenticated);

    final down = FakeAuthGateway()..serverUnreachable = true;
    final offline = AuthController(down);
    await offline.bootstrap();
    expect(offline.status, AuthStatus.unauthenticated);
  });

  test('login success authenticates, wrong password throws and changes nothing', () async {
    final auth = AuthController(FakeAuthGateway());
    await auth.bootstrap();

    await expectLater(auth.login('test@example.com', 'wrong'),
        throwsA(isA<ApiException>().having((e) => e.code, 'code', 'INVALID_CREDENTIALS')));
    expect(auth.status, AuthStatus.unauthenticated);

    await auth.login('test@example.com', 'password123');
    expect(auth.status, AuthStatus.authenticated);
  });

  test('register signs the new user in; duplicate email surfaces the server error', () async {
    final auth = AuthController(FakeAuthGateway());
    await auth.bootstrap();
    await expectLater(auth.register('A', 'taken@example.com', 'password123'),
        throwsA(isA<ApiException>().having((e) => e.code, 'code', 'EMAIL_ALREADY_REGISTERED')));
    expect(auth.status, AuthStatus.unauthenticated);

    await auth.register('A', 'new@example.com', 'password123');
    expect(auth.status, AuthStatus.authenticated);
  });

  test('logout and session expiry clear the user and notify listeners', () async {
    final gateway = FakeAuthGateway(sessionUser: testUser());
    final auth = AuthController(gateway);
    await auth.bootstrap();
    var notifications = 0;
    auth.addListener(() => notifications++);

    await auth.logout();
    expect(auth.status, AuthStatus.unauthenticated);
    expect(auth.user, isNull);
    expect(gateway.logoutCalls, 1);

    await auth.login('x@example.com', 'password123');
    auth.sessionExpired();
    expect(auth.status, AuthStatus.unauthenticated);
    expect(notifications, greaterThanOrEqualTo(3));
  });
}
