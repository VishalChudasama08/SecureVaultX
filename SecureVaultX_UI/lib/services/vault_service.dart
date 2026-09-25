import 'dart:io';

import 'package:dio/dio.dart' show CancelToken;

import '../core/api_client.dart';
import '../models/storage_policy.dart';
import '../models/vault_file.dart';

/// Everything the app does with files on the server. Abstract so tests can fake it.
abstract class VaultGateway {
  Future<StoragePolicy> policy();

  Future<List<VaultFile>> list();

  /// Mode A: encrypt [source] into the server vault.
  Future<VaultFile> store(File source, String filename, {TransferProgress? onProgress, CancelToken? cancel});

  /// Vault file as the original (decrypted on the server after full authentication).
  Future<DownloadedFile> downloadDecrypted(String id, File destination,
      {TransferProgress? onProgress, CancelToken? cancel});

  /// Vault file exactly as stored (still encrypted).
  Future<DownloadedFile> downloadEncrypted(String id, File destination,
      {TransferProgress? onProgress, CancelToken? cancel});

  Future<void> delete(String id);

  /// Mode B: upload [source], receive the encrypted .enc into [destination]. The server keeps no file.
  Future<DownloadedFile> encryptForDownload(File source, String filename, File destination,
      {TransferProgress? onUpload, TransferProgress? onDownload, CancelToken? cancel});

  /// Upload a .enc made for this account, receive the original into [destination].
  Future<DownloadedFile> decryptUpload(File source, File destination,
      {TransferProgress? onUpload, TransferProgress? onDownload, CancelToken? cancel});
}

class VaultService implements VaultGateway {
  VaultService(this._client);

  final ApiClient _client;

  @override
  Future<StoragePolicy> policy() async =>
      StoragePolicy.fromJson(await _client.getJson('/api/files/policy') as Map<String, dynamic>);

  @override
  Future<List<VaultFile>> list() async {
    final json = await _client.getJson('/api/vault/files') as List<dynamic>;
    return json.map((e) => VaultFile.fromJson(e as Map<String, dynamic>)).toList();
  }

  @override
  Future<VaultFile> store(File source, String filename,
      {TransferProgress? onProgress, CancelToken? cancel}) async {
    final json = await _client.uploadFile('/api/vault/files', source,
        filename: filename, onProgress: onProgress, cancelToken: cancel);
    return VaultFile.fromJson(json as Map<String, dynamic>);
  }

  @override
  Future<DownloadedFile> downloadDecrypted(String id, File destination,
          {TransferProgress? onProgress, CancelToken? cancel}) =>
      _client.downloadFile('/api/vault/files/$id/content', destination,
          onProgress: onProgress, cancelToken: cancel);

  @override
  Future<DownloadedFile> downloadEncrypted(String id, File destination,
          {TransferProgress? onProgress, CancelToken? cancel}) =>
      _client.downloadFile('/api/vault/files/$id/encrypted', destination,
          onProgress: onProgress, cancelToken: cancel);

  @override
  Future<void> delete(String id) => _client.delete('/api/vault/files/$id');

  @override
  Future<DownloadedFile> encryptForDownload(File source, String filename, File destination,
          {TransferProgress? onUpload, TransferProgress? onDownload, CancelToken? cancel}) =>
      _client.transformFile('/api/files/encrypt', source, destination,
          filename: filename,
          onUploadProgress: onUpload,
          onDownloadProgress: onDownload,
          cancelToken: cancel);

  @override
  Future<DownloadedFile> decryptUpload(File source, File destination,
          {TransferProgress? onUpload, TransferProgress? onDownload, CancelToken? cancel}) =>
      _client.transformFile('/api/files/decrypt', source, destination,
          onUploadProgress: onUpload, onDownloadProgress: onDownload, cancelToken: cancel);
}
