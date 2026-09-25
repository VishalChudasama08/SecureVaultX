import 'package:flutter/material.dart';

import 'app.dart';
import 'core/api_client.dart';
import 'services/auth_service.dart';
import 'services/local_file_service.dart';
import 'services/vault_service.dart';
import 'state/auth_controller.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();

  final client = await ApiClient.create();
  final auth = AuthController(AuthService(client));
  // A 401 on any authenticated call (session timed out on the server) sends the user back to login.
  client.onSessionExpired = auth.sessionExpired;

  runApp(SecureVaultXApp(auth: auth, vault: VaultService(client), files: LocalFileService()));
}
