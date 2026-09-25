/// Extracts the file name from a Content-Disposition header. Untrusted input: the result is reduced to a
/// plain file name (no directories, no control characters) before it is ever used to suggest a save name.
String? parseContentDisposition(String? header) {
  if (header == null || header.isEmpty) return null;

  // RFC 5987 form (what Spring emits for non-ASCII names): filename*=UTF-8''percent-encoded
  final star = RegExp(r"filename\*\s*=\s*([^']*)'[^']*'([^;]+)", caseSensitive: false).firstMatch(header);
  if (star != null) {
    try {
      return _clean(Uri.decodeComponent(star.group(2)!.trim()));
    } on FormatException {
      // fall through to the plain form
    } on ArgumentError {
      // fall through to the plain form
    }
  }
  final quoted = RegExp(r'filename\s*=\s*"((?:[^"\\]|\\.)*)"', caseSensitive: false).firstMatch(header);
  if (quoted != null) {
    return _clean(quoted.group(1)!.replaceAllMapped(RegExp(r'\\(.)'), (m) => m.group(1)!));
  }
  final bare = RegExp(r'filename\s*=\s*([^;\s]+)', caseSensitive: false).firstMatch(header);
  return bare == null ? null : _clean(bare.group(1)!);
}

String? _clean(String raw) {
  final lastSeparator = raw.lastIndexOf(RegExp(r'[\\/]'));
  var name = lastSeparator >= 0 ? raw.substring(lastSeparator + 1) : raw;
  name = name.replaceAll(RegExp(r'[\u0000-\u001F\u007F]'), '').trim();
  while (name.startsWith('.')) {
    name = name.substring(1);
  }
  return name.isEmpty ? null : name;
}
