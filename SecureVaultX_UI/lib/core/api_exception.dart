import 'dart:convert';

/// Every failure the UI can see. Backend errors are RFC 9457 problem+json with a stable `code`.
class ApiException implements Exception {
  ApiException({
    required this.code,
    this.status,
    this.detail = '',
    this.fieldErrors = const {},
  });

  final String code;
  final int? status;
  final String detail;
  final Map<String, String> fieldErrors;

  bool get isUnauthenticated => code == 'UNAUTHENTICATED' || status == 401 && code != 'INVALID_CREDENTIALS';

  /// Parses a problem+json body (already decoded text). Tolerates non-JSON bodies.
  factory ApiException.fromBody(int? status, String? body) {
    if (body != null && body.trim().isNotEmpty) {
      try {
        final decoded = jsonDecode(body);
        if (decoded is Map<String, dynamic>) {
          final errors = decoded['errors'];
          return ApiException(
            code: (decoded['code'] as String?) ?? _codeForStatus(status),
            status: status,
            detail: (decoded['detail'] as String?) ?? '',
            fieldErrors: errors is Map
                ? errors.map((k, v) => MapEntry(k.toString(), v.toString()))
                : const {},
          );
        }
      } on FormatException {
        // fall through to the status-based code
      }
    }
    return ApiException(code: _codeForStatus(status), status: status);
  }

  static String _codeForStatus(int? status) => switch (status) {
        401 => 'UNAUTHENTICATED',
        403 => 'FORBIDDEN',
        404 => 'NOT_FOUND',
        413 => 'UPLOAD_TOO_LARGE',
        _ => 'UNKNOWN',
      };

  /// Text that is safe and understandable to show to a person.
  String get friendlyMessage => switch (code) {
        'INVALID_CREDENTIALS' => 'Incorrect email or password.',
        'EMAIL_ALREADY_REGISTERED' => 'An account with this email already exists.',
        'VALIDATION_FAILED' =>
          fieldErrors.isNotEmpty ? fieldErrors.values.first : 'Please check the entered values.',
        'PASSWORD_TOO_LONG' => 'Password is too long.',
        'VAULT_SIZE_LIMIT_EXCEEDED' =>
          'This file is larger than the vault limit. Use "Encrypt & Download" instead.',
        'UPLOAD_TOO_LARGE' => 'This file exceeds the maximum upload size of the server.',
        'LENGTH_REQUIRED' => 'The upload could not be sized. Please try again.',
        'ENCRYPTED_FILE_INVALID' =>
          'This is not a valid SecureVaultX encrypted file, or it is incomplete.',
        'UNSUPPORTED_FORMAT_VERSION' =>
          'This encrypted file was made with a format version this server cannot read.',
        'INTEGRITY_CHECK_FAILED' =>
          'The file is corrupted or has been modified. Nothing was decrypted.',
        'RECORD_NOT_FOUND' =>
          'This file was not found for your account. It may belong to another account or was deleted.',
        'STORED_FILE_CORRUPT' => 'The stored file failed its integrity check.',
        'UNAUTHENTICATED' => 'Your session has expired. Please sign in again.',
        'CSRF_INVALID' => 'The request could not be verified. Please try again.',
        'NETWORK' => 'Cannot reach the server. Check your connection and the server address.',
        'DOWNLOAD_INCOMPLETE' => 'The transfer was interrupted. No file was saved.',
        'CANCELLED' => 'Cancelled.',
        _ => 'Something went wrong. Please try again.',
      };

  @override
  String toString() => 'ApiException($code, status: $status)';
}
