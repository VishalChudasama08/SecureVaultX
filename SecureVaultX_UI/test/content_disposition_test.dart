import 'package:flutter_test/flutter_test.dart';
import 'package:securevaultx_ui/core/content_disposition.dart';

void main() {
  test('reads RFC 5987 encoded UTF-8 names', () {
    expect(parseContentDisposition("attachment; filename*=UTF-8''r%C3%A9sum%C3%A9.pdf"), 'résumé.pdf');
  });

  test('reads plain quoted and bare names', () {
    expect(parseContentDisposition('attachment; filename="report.pdf"'), 'report.pdf');
    expect(parseContentDisposition('attachment; filename=report.pdf'), 'report.pdf');
  });

  test('never returns directories, hidden names or control characters', () {
    expect(parseContentDisposition('attachment; filename="../../etc/passwd"'), 'passwd');
    expect(parseContentDisposition(r'attachment; filename="C:\dir\x.txt"'), 'x.txt');
    expect(parseContentDisposition('attachment; filename=".hidden"'), 'hidden');
  });

  test('returns null when there is no usable name', () {
    expect(parseContentDisposition(null), isNull);
    expect(parseContentDisposition(''), isNull);
    expect(parseContentDisposition('attachment'), isNull);
    expect(parseContentDisposition('attachment; filename=""'), isNull);
  });
}
