class VaultFile {
  const VaultFile({required this.id, required this.filename, required this.size, required this.createdAt});

  final String id;
  final String filename;
  final int size;
  final DateTime createdAt;

  factory VaultFile.fromJson(Map<String, dynamic> json) => VaultFile(
        id: json['id'] as String,
        filename: json['filename'] as String,
        size: (json['size'] as num).toInt(),
        createdAt: DateTime.parse(json['createdAt'] as String),
      );
}
