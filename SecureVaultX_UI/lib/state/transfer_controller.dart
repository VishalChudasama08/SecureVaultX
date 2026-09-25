import 'package:dio/dio.dart' show CancelToken;
import 'package:flutter/foundation.dart';

import '../core/api_exception.dart';

/// Progress + cancellation for ONE long-running upload/download, shared by the Encrypt, Vault and Decrypt screens.
class TransferController extends ChangeNotifier {
  bool _active = false;
  String _label = '';
  double? _progress; // null = indeterminate
  CancelToken? _token;

  bool get active => _active;
  String get label => _label;
  double? get progress => _progress;

  void cancel() => _token?.cancel('cancelled by user');

  /// Runs [task]. Returns its result, or null if the user cancelled. Other failures are rethrown as
  /// [ApiException] for the caller to display. The caller decides how to weigh upload and download
  /// progress by calling [update].
  Future<T?> run<T>(String label, Future<T> Function(CancelToken token) task) async {
    if (_active) throw StateError('A transfer is already running');
    _token = CancelToken();
    _active = true;
    _label = label;
    _progress = null;
    notifyListeners();
    try {
      return await task(_token!);
    } on ApiException catch (e) {
      if (e.code == 'CANCELLED') return null;
      rethrow;
    } finally {
      _active = false;
      _token = null;
      _progress = null;
      notifyListeners();
    }
  }

  /// [done]/[total] in bytes; [from]..[to] is the slice of the overall bar this phase covers.
  void update(int done, int total, {double from = 0, double to = 1, String? label}) {
    if (label != null) _label = label;
    _progress = total > 0 ? from + (to - from) * (done / total).clamp(0.0, 1.0) : null;
    notifyListeners();
  }
}
