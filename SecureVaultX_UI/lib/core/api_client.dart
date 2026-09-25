import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:cookie_jar/cookie_jar.dart';
import 'package:dio/dio.dart';
import 'package:dio_cookie_manager/dio_cookie_manager.dart';
import 'package:path_provider/path_provider.dart';

import 'api_exception.dart';
import 'app_config.dart';
import 'content_disposition.dart';

typedef TransferProgress = void Function(int done, int total);

/// A finished, verified download on disk plus the file name the server suggested (if any).
class DownloadedFile {
  const DownloadedFile(this.file, this.suggestedName);

  final File file;
  final String? suggestedName;
}

/// The ONLY place that talks HTTP. Screens and services never build requests themselves.
///
/// Responsibilities: base URL, timeouts, session cookie persistence, CSRF token handling (fetch on demand,
/// refresh + single retry on CSRF_INVALID), problem+json error parsing, streaming upload and streaming
/// download to disk, and reporting an expired session.
class ApiClient {
  ApiClient(this._dio, this._cookies) {
    _dio.interceptors.add(CookieManager(_cookies));
    _dio.interceptors.add(InterceptorsWrapper(onError: (error, handler) {
      final path = error.requestOptions.path;
      final isAuthProbe = path.endsWith('/api/auth/login') || path.endsWith('/api/auth/me');
      if (error.response?.statusCode == 401 && !isAuthProbe) {
        onSessionExpired?.call();
      }
      handler.next(error);
    }));
  }

  /// Opens the persistent cookie jar in the app support directory and builds the client.
  static Future<ApiClient> create({String? baseUrl}) async {
    final dir = await getApplicationSupportDirectory();
    final jar = PersistCookieJar(storage: FileStorage('${dir.path}/cookies'));
    final dio = Dio(BaseOptions(
      baseUrl: baseUrl ?? AppConfig.apiBaseUrl,
      connectTimeout: const Duration(seconds: 15),
      // Plain text everywhere: we decode JSON ourselves so problem+json is handled uniformly.
      responseType: ResponseType.plain,
      headers: {'Accept': 'application/json'},
    ));
    return ApiClient(dio, jar);
  }

  final Dio _dio;
  final CookieJar _cookies;

  /// Called when an authenticated request comes back 401 (session expired on the server).
  void Function()? onSessionExpired;

  String? _csrfToken;
  String _csrfHeader = 'X-CSRF-TOKEN';

  /// Must be called after login/logout: Spring rotates the CSRF token together with the session.
  void forgetCsrfToken() => _csrfToken = null;

  Future<void> clearCookies() => _cookies.deleteAll();

  // ---- simple JSON calls ----------------------------------------------------------------------------------

  Future<dynamic> getJson(String path) async {
    final response = await _guard(() => _dio.get<String>(path));
    return _decode(response.data);
  }

  Future<dynamic> postJson(String path, Map<String, dynamic> body) async {
    final response = await _mutating<String>((csrf) => _dio.post<String>(
          path,
          data: body,
          options: Options(headers: csrf, contentType: Headers.jsonContentType),
        ));
    return _decode(response.data);
  }

  Future<void> postForm(String path, Map<String, String> fields) async {
    await _mutating<String>((csrf) => _dio.post<String>(
          path,
          data: fields,
          options: Options(headers: csrf, contentType: Headers.formUrlEncodedContentType),
        ));
  }

  Future<void> postEmpty(String path) async {
    await _mutating<String>((csrf) => _dio.post<String>(path, options: Options(headers: csrf)));
  }

  Future<void> delete(String path) async {
    await _mutating<String>((csrf) => _dio.delete<String>(path, options: Options(headers: csrf)));
  }

  // ---- file transfers -----------------------------------------------------------------------------------------

  /// Streams [source] to the server as the raw request body (constant memory) and returns the JSON reply.
  Future<dynamic> uploadFile(
    String path,
    File source, {
    required String filename,
    TransferProgress? onProgress,
    CancelToken? cancelToken,
  }) async {
    final length = await source.length();
    final response = await _mutating<String>((csrf) => _dio.post<String>(
          path,
          data: source.openRead(), // fresh stream per attempt
          options: Options(headers: {
            ...csrf,
            Headers.contentLengthHeader: length,
            'X-Filename': Uri.encodeComponent(filename),
          }, contentType: 'application/octet-stream'),
          onSendProgress: onProgress,
          cancelToken: cancelToken,
        ));
    return _decode(response.data);
  }

  /// Streams [source] up and the reply straight into [destination] on disk. On any failure the partially
  /// written file is deleted, so a corrupt file can never be mistaken for a finished one.
  Future<DownloadedFile> transformFile(
    String path,
    File source,
    File destination, {
    String? filename,
    TransferProgress? onUploadProgress,
    TransferProgress? onDownloadProgress,
    CancelToken? cancelToken,
  }) async {
    final length = await source.length();
    final response = await _mutating<ResponseBody>((csrf) => _dio.post<ResponseBody>(
          path,
          data: source.openRead(),
          options: Options(
            headers: {
              ...csrf,
              Headers.contentLengthHeader: length,
              if (filename != null) 'X-Filename': Uri.encodeComponent(filename),
            },
            contentType: 'application/octet-stream',
            responseType: ResponseType.stream,
          ),
          onSendProgress: onUploadProgress,
          cancelToken: cancelToken,
        ));
    return _writeToDisk(response, destination, onDownloadProgress);
  }

  /// GET a binary resource into [destination] (same integrity rules as [transformFile]).
  Future<DownloadedFile> downloadFile(
    String path,
    File destination, {
    TransferProgress? onProgress,
    CancelToken? cancelToken,
  }) async {
    final response = await _guard(() => _dio.get<ResponseBody>(
          path,
          options: Options(responseType: ResponseType.stream),
          cancelToken: cancelToken,
        ));
    return _writeToDisk(response, destination, onProgress);
  }

  // ---- internals ------------------------------------------------------------------------------------------------

  Future<DownloadedFile> _writeToDisk(
    Response<ResponseBody> response,
    File destination,
    TransferProgress? onProgress,
  ) async {
    final expected = int.tryParse(response.headers.value(Headers.contentLengthHeader) ?? '');
    final sink = destination.openWrite();
    var received = 0;
    try {
      await for (final chunk in response.data!.stream) {
        sink.add(chunk);
        received += chunk.length;
        onProgress?.call(received, expected ?? -1);
      }
      await sink.flush();
      await sink.close();
    } catch (error) {
      await _discard(sink, destination);
      throw _translate(error);
    }
    if (expected != null && received != expected) {
      await _delete(destination);
      throw ApiException(code: 'DOWNLOAD_INCOMPLETE');
    }
    return DownloadedFile(
      destination,
      parseContentDisposition(response.headers.value('content-disposition')),
    );
  }

  Future<void> _discard(IOSink sink, File file) async {
    try {
      await sink.close();
    } catch (_) {
      // already broken; the delete below is what matters
    }
    await _delete(file);
  }

  Future<void> _delete(File file) async {
    try {
      if (await file.exists()) await file.delete();
    } catch (_) {
      // best effort
    }
  }

  Future<Response<T>> _mutating<T>(Future<Response<T>> Function(Map<String, String> csrf) send) async {
    for (var attempt = 0;; attempt++) {
      if (_csrfToken == null) await _refreshCsrf();
      try {
        return await send({_csrfHeader: _csrfToken!});
      } on DioException catch (error) {
        final api = await _fromDio(error);
        if (attempt == 0 && api.code == 'CSRF_INVALID') {
          _csrfToken = null; // token rotated or session changed: fetch a new one and retry once
          continue;
        }
        throw api;
      }
    }
  }

  Future<Response<T>> _guard<T>(Future<Response<T>> Function() send) async {
    try {
      return await send();
    } on DioException catch (error) {
      throw await _fromDio(error);
    }
  }

  Future<void> _refreshCsrf() async {
    final response = await _guard(() => _dio.get<String>('/api/auth/csrf'));
    final map = _decode(response.data) as Map<String, dynamic>;
    _csrfHeader = map['headerName'] as String;
    _csrfToken = map['token'] as String;
  }

  dynamic _decode(String? body) => (body == null || body.isEmpty) ? null : jsonDecode(body);

  Future<ApiException> _fromDio(DioException error) async {
    if (error.type == DioExceptionType.cancel) return ApiException(code: 'CANCELLED');
    final response = error.response;
    if (response == null) return ApiException(code: 'NETWORK');
    final data = response.data;
    String? body;
    if (data is String) {
      body = data;
    } else if (data is ResponseBody) {
      final bytes = <int>[];
      await for (final chunk in data.stream) {
        bytes.addAll(chunk);
      }
      body = utf8.decode(bytes, allowMalformed: true);
    }
    return ApiException.fromBody(response.statusCode, body);
  }

  ApiException _translate(Object error) {
    if (error is ApiException) return error;
    if (error is DioException) {
      return error.type == DioExceptionType.cancel
          ? ApiException(code: 'CANCELLED')
          : ApiException(code: 'DOWNLOAD_INCOMPLETE');
    }
    return ApiException(code: 'DOWNLOAD_INCOMPLETE');
  }
}
