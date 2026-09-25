import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import 'routing/app_router.dart';
import 'services/local_file_service.dart';
import 'services/vault_service.dart';
import 'state/auth_controller.dart';
import 'theme/app_theme.dart';

/// Root widget. All collaborators are injected so tests can run the full UI against fakes.
class SecureVaultXApp extends StatefulWidget {
  const SecureVaultXApp({super.key, required this.auth, required this.vault, required this.files});

  final AuthController auth;
  final VaultGateway vault;
  final LocalFileService files;

  @override
  State<SecureVaultXApp> createState() => _SecureVaultXAppState();
}

class _SecureVaultXAppState extends State<SecureVaultXApp> {
  late final GoRouter _router = buildRouter(widget.auth);

  @override
  void initState() {
    super.initState();
    widget.auth.bootstrap();
  }

  @override
  void dispose() {
    _router.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return MultiProvider(
      providers: [
        ChangeNotifierProvider<AuthController>.value(value: widget.auth),
        Provider<VaultGateway>.value(value: widget.vault),
        Provider<LocalFileService>.value(value: widget.files),
      ],
      child: MaterialApp.router(
        title: 'SecureVaultX',
        theme: AppTheme.light(),
        darkTheme: AppTheme.dark(),
        routerConfig: _router,
      ),
    );
  }
}
