import 'package:flutter_test/flutter_test.dart';
import 'package:securevaultx_ui/core/api_exception.dart';

void main() {
  test('parses problem+json with a stable code', () {
    final e = ApiException.fromBody(
        409, '{"type":"about:blank","title":"Conflict","status":409,"detail":"x","code":"EMAIL_ALREADY_REGISTERED"}');
    expect(e.code, 'EMAIL_ALREADY_REGISTERED');
    expect(e.status, 409);
    expect(e.friendlyMessage, contains('already exists'));
  });

  test('collects field errors of a validation failure', () {
    final e = ApiException.fromBody(
        400, '{"code":"VALIDATION_FAILED","errors":{"email":"Email is not valid","password":"Too short"}}');
    expect(e.code, 'VALIDATION_FAILED');
    expect(e.fieldErrors['email'], 'Email is not valid');
    expect(e.friendlyMessage, 'Email is not valid');
  });

  test('falls back to the status code for non-JSON or empty bodies', () {
    expect(ApiException.fromBody(401, '').code, 'UNAUTHENTICATED');
    expect(ApiException.fromBody(413, '<html>too big</html>').code, 'UPLOAD_TOO_LARGE');
    expect(ApiException.fromBody(500, null).code, 'UNKNOWN');
  });

  test('a failed login is not treated as an expired session', () {
    expect(ApiException(code: 'INVALID_CREDENTIALS', status: 401).isUnauthenticated, isFalse);
    expect(ApiException(code: 'UNAUTHENTICATED', status: 401).isUnauthenticated, isTrue);
  });

  test('every user-facing message hides technical detail', () {
    for (final code in ['INTEGRITY_CHECK_FAILED', 'STORED_FILE_CORRUPT', 'NETWORK', 'DOWNLOAD_INCOMPLETE', 'weird']) {
      final message = ApiException(code: code, detail: 'java.lang.NullPointerException at ...').friendlyMessage;
      expect(message, isNot(contains('java')));
      expect(message, isNotEmpty);
    }
  });
}
