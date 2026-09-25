/// Server-side limits, fetched from GET /api/files/policy so the UI never hard-codes "5 MB".
class StoragePolicy {
  const StoragePolicy({required this.maxVaultFileSizeBytes, required this.maxUploadSizeBytes});

  final int maxVaultFileSizeBytes;
  final int maxUploadSizeBytes;

  factory StoragePolicy.fromJson(Map<String, dynamic> json) => StoragePolicy(
        maxVaultFileSizeBytes: (json['maxVaultFileSizeBytes'] as num).toInt(),
        maxUploadSizeBytes: (json['maxUploadSizeBytes'] as num).toInt(),
      );

  bool allowsVault(int fileSize) => fileSize <= maxVaultFileSizeBytes;
}
