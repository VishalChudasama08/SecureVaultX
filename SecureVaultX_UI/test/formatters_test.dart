import 'package:flutter_test/flutter_test.dart';
import 'package:securevaultx_ui/core/formatters.dart';

void main() {
  test('formatBytes uses binary units', () {
    expect(formatBytes(0), '0 B');
    expect(formatBytes(1023), '1023 B');
    expect(formatBytes(1024), '1 KB');
    expect(formatBytes(1536), '1.5 KB');
    expect(formatBytes(5 * 1024 * 1024), '5 MB');
    expect(formatBytes(5 * 1024 * 1024 + 1), '5.0 MB');
    expect(formatBytes(3 * 1024 * 1024 * 1024), '3 GB');
  });
}
