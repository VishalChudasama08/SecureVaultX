import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../core/api_exception.dart';
import '../core/formatters.dart';
import '../models/storage_policy.dart';
import '../routing/app_router.dart';
import '../services/local_file_service.dart';
import '../services/vault_service.dart';
import '../state/transfer_controller.dart';
import '../widgets/common.dart';

enum EncryptMode { vault, download }

class EncryptScreen extends StatefulWidget {
  const EncryptScreen({super.key});

  @override
  State<EncryptScreen> createState() => _EncryptScreenState();
}

class _EncryptScreenState extends State<EncryptScreen> {
  final _transfer = TransferController();
  PickedFile? _file;
  StoragePolicy? _policy;
  EncryptMode _mode = EncryptMode.vault;
  String? _error;

  @override
  void initState() {
    super.initState();
    _loadPolicy();
  }

  @override
  void dispose() {
    _transfer.dispose();
    super.dispose();
  }

  Future<void> _loadPolicy() async {
    try {
      final policy = await context.read<VaultGateway>().policy();
      if (mounted) setState(() => _policy = policy);
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.friendlyMessage);
    }
  }

  bool get _vaultAllowed => _file != null && _policy != null && _policy!.allowsVault(_file!.size);

  Future<void> _pick() async {
    final picked = await context.read<LocalFileService>().pickFile();
    if (picked == null || !mounted) return;
    setState(() {
      _file = picked;
      _error = null;
      // The vault is size-limited by server policy: fall back to Encrypt & Download for larger files.
      _mode = (_policy != null && _policy!.allowsVault(picked.size)) ? _mode : EncryptMode.download;
    });
  }

  Future<void> _encrypt() async {
    final file = _file;
    if (file == null || _transfer.active) return;
    setState(() => _error = null);
    final vault = context.read<VaultGateway>();
    final files = context.read<LocalFileService>();
    try {
      if (_mode == EncryptMode.vault) {
        final stored = await _transfer.run('Encrypting and storing', (cancel) => vault.store(
              file.file,
              file.name,
              onProgress: (d, t) => _transfer.update(d, t),
              cancel: cancel,
            ));
        if (stored == null || !mounted) return;
        showSnack(context, '"${stored.filename}" is stored in your vault.');
        setState(() => _file = null);
        context.go(Routes.vault);
      } else {
        final temp = await files.newTempFile('.enc.part');
        final result = await _transfer.run('Encrypting', (cancel) => vault.encryptForDownload(
              file.file,
              file.name,
              temp,
              onUpload: (d, t) => _transfer.update(d, t, from: 0, to: 0.5, label: 'Uploading and encrypting'),
              onDownload: (d, t) => _transfer.update(d, t, from: 0.5, to: 1, label: 'Receiving encrypted file'),
              cancel: cancel,
            ));
        if (result == null) {
          await files.deleteQuietly(temp);
          return;
        }
        final saved = await files.saveFile(result.file, result.suggestedName ?? '${file.name}.enc');
        if (!mounted) return;
        if (saved == null) {
          showSnack(context, 'Not saved. The encrypted file was discarded and cannot be recovered.');
        } else {
          showSnack(context, 'Encrypted file saved. Keep it safe: you need it to decrypt later.');
          setState(() => _file = null);
        }
      }
    } on ApiException catch (e) {
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
              Text('Encrypt a file', style: theme.textTheme.headlineSmall),
              const SizedBox(height: 16),
              if (_error != null) ...[ErrorBanner(_error!), const SizedBox(height: 16)],
              Card.outlined(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                    Row(children: [
                      const Icon(Icons.insert_drive_file_outlined),
                      const SizedBox(width: 12),
                      Expanded(
                        child: file == null
                            ? const Text('No file selected')
                            : Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                                Text(file.name, maxLines: 2, overflow: TextOverflow.ellipsis),
                                Text(formatBytes(file.size), style: theme.textTheme.bodySmall),
                              ]),
                      ),
                      OutlinedButton(onPressed: busy ? null : _pick, child: Text(file == null ? 'Choose file' : 'Change')),
                    ]),
                  ]),
                ),
              ),
              const SizedBox(height: 16),
              Text('Where should the encrypted file go?', style: theme.textTheme.titleSmall),
              const SizedBox(height: 8),
              SegmentedButton<EncryptMode>(
                segments: [
                  ButtonSegment(
                    value: EncryptMode.vault,
                    icon: const Icon(Icons.folder_outlined),
                    label: const Text('Secure vault'),
                    enabled: file == null || _vaultAllowed,
                  ),
                  const ButtonSegment(
                    value: EncryptMode.download,
                    icon: Icon(Icons.download_outlined),
                    label: Text('Encrypt & download'),
                  ),
                ],
                selected: {_mode},
                onSelectionChanged: busy ? null : (s) => setState(() => _mode = s.first),
              ),
              const SizedBox(height: 8),
              Text(
                _mode == EncryptMode.vault
                    ? 'The encrypted file is kept on the server${_policy == null ? '' : ' (up to ${formatBytes(_policy!.maxVaultFileSizeBytes)})'}. '
                        'You can download or delete it later from your vault.'
                    : 'You receive the encrypted .enc file and the server keeps no copy. Works for any size. '
                        'Keep the .enc file: it is the only copy.',
                style: theme.textTheme.bodySmall,
              ),
              if (file != null && _policy != null && !_policy!.allowsVault(file.size)) ...[
                const SizedBox(height: 8),
                Text(
                  'This file is larger than the vault limit (${formatBytes(_policy!.maxVaultFileSizeBytes)}), '
                  'so only Encrypt & download is available.',
                  style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.primary),
                ),
              ],
              const SizedBox(height: 24),
              FilledButton.icon(
                onPressed: (file == null || busy || _policy == null) ? null : _encrypt,
                icon: const Icon(Icons.lock),
                label: const Text('Encrypt'),
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
