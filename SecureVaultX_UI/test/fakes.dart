import 'dart:io';

import 'package:dio/dio.dart' show CancelToken;
import 'package:securevaultx_ui/core/api_client.dart';
import 'package:securevaultx_ui/core/api_exception.dart';
import 'package:securevaultx_ui/models/app_user.dart';
import 'package:securevaultx_ui/models/storage_policy.dart';
import 'package:securevaultx_ui/models/vault_file.dart';
import 'package:securevaultx_ui/services/auth_service.dart';
import 'package:securevaultx_ui/services/vault_service.dart';

AppUser testUser() => AppUser(fullName: 'Test User', email: 'test@example.com', createdAt: DateTime.utc(2026, 1, 1));

class FakeAuthGateway implements AuthGateway {
  FakeAuthGateway({this.sessionUser, this.password = 'password123'});

  /// Non-null = the server already has a valid session for this user (persisted cookie).
  AppUser? sessionUser;
  final String password;
  bool serverUnreachable = false;
  int logoutCalls = 0;

  @override
  Future<AppUser?> currentUser() async {
    if (serverUnreachable) throw ApiException(code: 'NETWORK');
    return sessionUser;
  }

  @override
  Future<AppUser> login({required String email, required String password}) async {
    if (password != this.password) throw ApiException(code: 'INVALID_CREDENTIALS', status: 401);
    return sessionUser = testUser();
  }

  @override
  Future<void> register({required String fullName, required String email, required String password}) async {
    if (email == 'taken@example.com') throw ApiException(code: 'EMAIL_ALREADY_REGISTERED', status: 409);
  }

  @override
  Future<void> logout() async {
    logoutCalls++;
    sessionUser = null;
  }
}

/// Only what the widget tests touch is implemented.
class FakeVaultGateway implements VaultGateway {
  FakeVaultGateway({List<VaultFile>? files}) : files = files ?? [];

  final List<VaultFile> files;

  @override
  Future<StoragePolicy> policy() async =>
      const StoragePolicy(maxVaultFileSizeBytes: 5 * 1024 * 1024, maxUploadSizeBytes: 2 * 1024 * 1024 * 1024);

  @override
  Future<List<VaultFile>> list() async => List.of(files);

  @override
  Future<void> delete(String id) async => files.removeWhere((f) => f.id == id);

  @override
  Future<VaultFile> store(File source, String filename, {TransferProgress? onProgress, CancelToken? cancel}) =>
      throw UnimplementedError();

  @override
  Future<DownloadedFile> downloadDecrypted(String id, File destination,
          {TransferProgress? onProgress, CancelToken? cancel}) =>
      throw UnimplementedError();

  @override
  Future<DownloadedFile> downloadEncrypted(String id, File destination,
          {TransferProgress? onProgress, CancelToken? cancel}) =>
      throw UnimplementedError();

  @override
  Future<DownloadedFile> encryptForDownload(File source, String filename, File destination,
          {TransferProgress? onUpload, TransferProgress? onDownload, CancelToken? cancel}) =>
      throw UnimplementedError();

  @override
  Future<DownloadedFile> decryptUpload(File source, File destination,
          {TransferProgress? onUpload, TransferProgress? onDownload, CancelToken? cancel}) =>
      throw UnimplementedError();
}
