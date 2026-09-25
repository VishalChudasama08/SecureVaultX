import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../routing/app_router.dart';
import '../state/auth_controller.dart';
import '../widgets/common.dart';

class HomeScreen extends StatelessWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context) {
    final user = context.watch<AuthController>().user;
    final theme = Theme.of(context);

    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: ContentWidth(
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text('Welcome${user == null ? '' : ', ${user.fullName}'}', style: theme.textTheme.headlineSmall),
          const SizedBox(height: 4),
          Text('What would you like to do?', style: theme.textTheme.bodyMedium),
          const SizedBox(height: 16),
          LayoutBuilder(builder: (context, constraints) {
            final columns = constraints.maxWidth >= 860 ? 3 : (constraints.maxWidth >= 560 ? 2 : 1);
            const gap = 16.0;
            final width = (constraints.maxWidth - gap * (columns - 1)) / columns;
            Widget card(IconData icon, String title, String text, String route) => SizedBox(
                  width: width,
                  child: _ActionCard(icon: icon, title: title, text: text, onTap: () => context.go(route)),
                );
            return Wrap(spacing: gap, runSpacing: gap, children: [
              card(Icons.lock, 'Encrypt a file',
                  'Store it in your secure vault, or encrypt and download it (any size).', Routes.encrypt),
              card(Icons.folder, 'Open my vault', 'Download, decrypt or delete files stored in your vault.',
                  Routes.vault),
              card(Icons.lock_open, 'Decrypt a file', 'Restore the original from an .enc file made with your account.',
                  Routes.decrypt),
            ]);
          }),
          const SizedBox(height: 24),
          Card.outlined(
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Row(crossAxisAlignment: CrossAxisAlignment.start, children: [
                const Icon(Icons.info_outline),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    'Files are encrypted on the SecureVaultX server with AES-256-GCM, and each file has its own key. '
                    'This is server-side encryption, not end-to-end: the server processes your file while '
                    'encrypting or decrypting it, so only connect to a server you trust, over HTTPS.',
                    style: theme.textTheme.bodySmall,
                  ),
                ),
              ]),
            ),
          ),
        ]),
      ),
    );
  }
}

class _ActionCard extends StatelessWidget {
  const _ActionCard({required this.icon, required this.title, required this.text, required this.onTap});

  final IconData icon;
  final String title;
  final String text;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Card.outlined(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
            Icon(icon, size: 32, color: theme.colorScheme.primary),
            const SizedBox(height: 12),
            Text(title, style: theme.textTheme.titleMedium),
            const SizedBox(height: 4),
            Text(text, style: theme.textTheme.bodySmall),
          ]),
        ),
      ),
    );
  }
}
