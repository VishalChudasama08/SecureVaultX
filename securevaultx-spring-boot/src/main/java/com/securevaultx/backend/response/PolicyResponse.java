package com.securevaultx.backend.response;

/** Lets the Flutter app show the vault limit from the server instead of hard-coding "5 MB". */
public record PolicyResponse(long maxVaultFileSizeBytes, long maxUploadSizeBytes) {
}
