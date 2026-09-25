import 'dart:io';

import 'package:dio/dio.dart' show CancelToken;
import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';
import 'package:provider/provider.dart';

import '../core/api_client.dart';
import '../core/api_exception.dart';
import '../core/formatters.dart';
import '../models/vault_file.dart';
import '../routing/app_router.dart';
import '../services/local_file_service.dart';
import '../services/vault_service.dart';
import '../state/transfer_controller.dart';
import '../widgets/common.dart';

enum _FileAction { decrypted, encrypted, delete }

class VaultScreen extends StatefulWidget {
  const VaultScreen({super.key});

  @override
  State<VaultScreen> createState() => _VaultScreenState();
}

class _VaultScreenState extends State<VaultScreen> {
  final _transfer = TransferController();
  List<VaultFile>? _files;
  String? _error;
  bool _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  @override
  void dispose() {
    _transfer.dispose();
    super.dispose();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final files = await context.read<VaultGateway>().list();
      if (mounted) setState(() => _files = files);
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.friendlyMessage);
    } finally {
      if (mounted) setState(() => _loading = false);
    }
  }

  Future<void> _download(VaultFile file, {required bool decrypted}) async {
    if (_transfer.active) return;
    final vault = context.read<VaultGateway>();
    final files = context.read<LocalFileService>();
    final temp = await files.newTempFile(decrypted ? '.dec.part' : '.enc.part');
    try {
      final Future<DownloadedFile> Function(String, File, {TransferProgress? onProgress, CancelToken? cancel}) fetch =
          decrypted ? vault.downloadDecrypted : vault.downloadEncrypted;
      final result = await _transfer.run(
        decrypted ? 'Verifying and decrypting' : 'Downloading encrypted file',
        (cancel) => fetch(file.id, temp, onProgress: (d, t) => _transfer.update(d, t), cancel: cancel),
      );
      if (result == null) {
        await files.deleteQuietly(temp);
        return;
      }
      final fallback = decrypted ? file.filename : '${file.filename}.enc';
      final saved = await files.saveFile(result.file, result.suggestedName ?? fallback);
      if (mounted && saved != null) showSnack(context, 'File saved.');
    } on ApiException catch (e) {
      await files.deleteQuietly(temp);
      if (mounted) setState(() => _error = e.friendlyMessage);
    }
  }

  Future<void> _delete(VaultFile file) async {
    final ok = await confirm(
      context,
      title: 'Delete file?',
      message: '"${file.filename}" will be permanently removed from your vault. This cannot be undone.',
      action: 'Delete',
    );
    if (!ok || !mounted) return;
    final vault = context.read<VaultGateway>();
    try {
      await vault.delete(file.id);
      if (!mounted) return;
      setState(() => _files = _files?.where((f) => f.id != file.id).toList());
      showSnack(context, 'Deleted "${file.filename}".');
    } on ApiException catch (e) {
      if (mounted) setState(() => _error = e.friendlyMessage);
    }
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return RefreshIndicator(
      onRefresh: _load,
      child: ListenableBuilder(
        listenable: _transfer,
        builder: (context, _) {
          final busy = _transfer.active;
          final files = _files;
          return ListView(
            physics: const AlwaysScrollableScrollPhysics(),
            padding: const EdgeInsets.all(16),
            children: [
              ContentWidth(
                child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
                  Row(children: [
                    Expanded(child: Text('My vault', style: theme.textTheme.headlineSmall)),
                    IconButton(tooltip: 'Refresh', onPressed: (_loading || busy) ? null : _load, icon: const Icon(Icons.refresh)),
                  ]),
                  const SizedBox(height: 8),
                  if (_error != null) ...[ErrorBanner(_error!), const SizedBox(height: 12)],
                  TransferCard(controller: _transfer),
                  if (busy) const SizedBox(height: 12),
                  if (_loading && files == null)
                    const Padding(padding: EdgeInsets.all(32), child: Center(child: CircularProgressIndicator()))
                  else if (files != null && files.isEmpty)
                    _EmptyVault(onEncrypt: () => context.go(Routes.encrypt))
                  else if (files != null)
                    Card.outlined(
                      clipBehavior: Clip.antiAlias,
                      child: Column(children: [
                        for (var i = 0; i < files.length; i++) ...[
                          if (i > 0) const Divider(height: 1),
                          _FileTile(
                            file: files[i],
                            enabled: !busy,
                            onAction: (a) => switch (a) {
                              _FileAction.decrypted => _download(files[i], decrypted: true),
                              _FileAction.encrypted => _download(files[i], decrypted: false),
                              _FileAction.delete => _delete(files[i]),
                            },
                          ),
                        ],
                      ]),
                    ),
                ]),
              ),
            ],
          );
        },
      ),
    );
  }
}

class _FileTile extends StatelessWidget {
  const _FileTile({required this.file, required this.enabled, required this.onAction});

  final VaultFile file;
  final bool enabled;
  final void Function(_FileAction) onAction;

  @override
  Widget build(BuildContext context) {
    return ListTile(
      leading: const Icon(Icons.lock_outline),
      title: Text(file.filename, maxLines: 1, overflow: TextOverflow.ellipsis),
      subtitle: Text('${formatBytes(file.size)}  •  ${formatDateTime(file.createdAt)}'),
      onTap: enabled ? () => onAction(_FileAction.decrypted) : null,
      trailing: PopupMenuButton<_FileAction>(
        enabled: enabled,
        tooltip: 'Actions',
        onSelected: onAction,
        itemBuilder: (context) => const [
          PopupMenuItem(value: _FileAction.decrypted, child: Text('Download (decrypted)')),
          PopupMenuItem(value: _FileAction.encrypted, child: Text('Download encrypted (.enc)')),
          PopupMenuItem(value: _FileAction.delete, child: Text('Delete')),
        ],
      ),
    );
  }
}

class _EmptyVault extends StatelessWidget {
  const _EmptyVault({required this.onEncrypt});

  final VoidCallback onEncrypt;

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 48),
      child: Column(children: [
        Icon(Icons.folder_open, size: 56, color: Theme.of(context).colorScheme.outline),
        const SizedBox(height: 12),
        const Text('Your vault is empty'),
        const SizedBox(height: 12),
        FilledButton(onPressed: onEncrypt, child: const Text('Encrypt a file')),
      ]),
    );
  }
}
