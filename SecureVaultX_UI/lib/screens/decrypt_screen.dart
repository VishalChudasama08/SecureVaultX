import 'package:flutter/material.dart';
import 'package:provider/provider.dart';

import '../core/api_exception.dart';
import '../core/formatters.dart';
import '../services/local_file_service.dart';
import '../services/vault_service.dart';
import '../state/transfer_controller.dart';
import '../widgets/common.dart';

class DecryptScreen extends StatefulWidget {
  const DecryptScreen({super.key});

  @override
  State<DecryptScreen> createState() => _DecryptScreenState();
}

class _DecryptScreenState extends State<DecryptScreen> {
  final _transfer = TransferController();
  PickedFile? _file;
  String? _error;

  @override
  void dispose() {
    _transfer.dispose();
    super.dispose();
  }

  Future<void> _pick() async {
    final picked = await context.read<LocalFileService>().pickFile();
    if (picked == null || !mounted) return;
    setState(() {
      _file = picked;
      _error = null;
    });
  }

  Future<void> _decrypt() async {
    final file = _file;
    if (file == null || _transfer.active) return;
    setState(() => _error = null);
    final vault = context.read<VaultGateway>();
    final files = context.read<LocalFileService>();
    final temp = await files.newTempFile('.dec.part');
    try {
      final result = await _transfer.run('Verifying and decrypting', (cancel) => vault.decryptUpload(
            file.file,
            temp,
            onUpload: (d, t) => _transfer.update(d, t, from: 0, to: 0.5, label: 'Uploading'),
            onDownload: (d, t) => _transfer.update(d, t, from: 0.5, to: 1, label: 'Receiving decrypted file'),
            cancel: cancel,
          ));
      if (result == null) {
        await files.deleteQuietly(temp);
        return;
      }
      final saved = await files.saveFile(result.file, result.suggestedName ?? 'decrypted-file');
      if (!mounted) return;
      if (saved == null) {
        showSnack(context, 'Not saved. The decrypted file was discarded.');
      } else {
        showSnack(context, 'Decrypted file saved.');
        setState(() => _file = null);
      }
    } on ApiException catch (e) {
      await files.deleteQuietly(temp);
      if (mounted) setState(() => _error = e.friendlyMessage);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final file = _file;

    return SingleChildScrollView(
      padding: const EdgeInsets.all(16),
      child: ContentWidth(
        maxWidth: 720,
        child: ListenableBuilder(
          listenable: _transfer,
          builder: (context, _) {
            final busy = _transfer.active;
            return Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
              Text('Decrypt a file', style: theme.textTheme.headlineSmall),
              const SizedBox(height: 4),
              Text(
                'Choose an .enc file that was created with your account. It is checked in full before any '
                'decrypted data is released; a modified or incomplete file is rejected.',
                style: theme.textTheme.bodySmall,
              ),
              const SizedBox(height: 16),
              if (_error != null) ...[ErrorBanner(_error!), const SizedBox(height: 16)],
              Card.outlined(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Row(children: [
                    const Icon(Icons.lock_outline),
                    const SizedBox(width: 12),
                    Expanded(
                      child: file == null
                          ? const Text('No file selected')
                          : Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                              Text(file.name, maxLines: 2, overflow: TextOverflow.ellipsis),
                              Text(formatBytes(file.size), style: theme.textTheme.bodySmall),
                            ]),
                    ),
                    OutlinedButton(onPressed: busy ? null : _pick, child: Text(file == null ? 'Choose .enc file' : 'Change')),
                  ]),
                ),
              ),
              const SizedBox(height: 24),
              FilledButton.icon(
                onPressed: (file == null || busy) ? null : _decrypt,
                icon: const Icon(Icons.lock_open),
                label: const Text('Decrypt'),
              ),
              const SizedBox(height: 16),
              TransferCard(controller: _transfer),
            ]);
          },
        ),
      ),
    );
  }
}
