import 'dart:io';
import 'dart:math';

import 'package:file_picker/file_picker.dart';
import 'package:path/path.dart' as p;
import 'package:path_provider/path_provider.dart';

import '../core/app_config.dart';

class PickedFile {
  const PickedFile({required this.file, required this.name, required this.size});

  final File file;
  final String name;
  final int size;
}

/// Everything platform-specific about files lives here: picking, temp files, and "save as".
/// Downloads land in a temp file first; only a complete, verified file is ever moved to its final place.
class LocalFileService {
  bool get _isDesktop => Platform.isWindows || Platform.isLinux || Platform.isMacOS;

  /// Lets the user choose a file. Only the path is returned: the content is streamed later, never loaded.
  Future<PickedFile?> pickFile() async {
    final result = await FilePicker.platform.pickFiles(withData: false, allowMultiple: false);
    if (result == null || result.files.isEmpty) return null;
    final picked = result.files.single;
    final path = picked.path;
    if (path == null) return null;
    return PickedFile(file: File(path), name: picked.name, size: picked.size);
  }

  Future<File> newTempFile([String suffix = '.part']) async {
    final dir = await getTemporaryDirectory();
    final id = Random.secure().nextInt(1 << 32).toRadixString(16);
    return File(p.join(dir.path, 'svx-$id$suffix'));
  }

  Future<void> deleteQuietly(File file) async {
    try {
      if (await file.exists()) await file.delete();
    } catch (_) {
      // temp files are also cleaned by the OS
    }
  }

  /// Asks the user where to save [source] and moves it there. Returns the final path, or null if cancelled.
  /// The temp file is always removed afterwards.
  Future<String?> saveFile(File source, String suggestedName) async {
    try {
      if (_isDesktop) {
        final target = await FilePicker.platform.saveFile(dialogTitle: 'Save file', fileName: suggestedName);
        if (target == null) return null;
        await _moveOrCopy(source, target);
        return target;
      }
      final size = await source.length();
      if (size <= AppConfig.mobileInMemorySaveLimitBytes) {
        // The mobile save dialog needs the bytes; bounded by the limit above.
        final target = await FilePicker.platform.saveFile(
          dialogTitle: 'Save file',
          fileName: suggestedName,
          bytes: await source.readAsBytes(),
        );
        return target;
      }
      // Very large files on mobile: keep them in the app documents folder instead of loading them into memory.
      final docs = await getApplicationDocumentsDirectory();
      final target = p.join(docs.path, suggestedName);
      await _moveOrCopy(source, target);
      return target;
    } finally {
      await deleteQuietly(source);
    }
  }

  Future<void> _moveOrCopy(File source, String target) async {
    try {
      await source.rename(target);
    } on FileSystemException {
      await source.copy(target); // rename fails across volumes
    }
  }
}
