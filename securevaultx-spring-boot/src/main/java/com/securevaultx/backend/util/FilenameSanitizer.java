package com.securevaultx.backend.util;

/**
 * Filenames from clients are untrusted display metadata. They are NEVER used to build filesystem paths
 * (storage uses generated UUIDs); this cleans them for display and for Content-Disposition headers.
 */
public final class FilenameSanitizer {

    public static final int MAX_LENGTH = 255;
    public static final String FALLBACK = "file";

    private static final String FORBIDDEN = "\"<>:*?|";

    private FilenameSanitizer() {
    }

    public static String sanitize(String raw) {
        if (raw == null) {
            return FALLBACK;
        }
        // keep only the last path component, whichever separator the client used
        String name = raw.substring(Math.max(raw.lastIndexOf('/'), raw.lastIndexOf('\\')) + 1);
        StringBuilder sb = new StringBuilder(name.length());
        name.codePoints().forEach(cp -> {
            boolean bad = Character.isISOControl(cp) || cp == 0x2028 || cp == 0x2029
                    || (cp < 128 && FORBIDDEN.indexOf(cp) >= 0);
            if (!bad) {
                sb.appendCodePoint(cp);
            }
        });
        String cleaned = sb.toString().strip();
        // leading dots would create hidden files / ".." names
        while (cleaned.startsWith(".")) {
            cleaned = cleaned.substring(1);
        }
        if (cleaned.isEmpty()) {
            return FALLBACK;
        }
        if (cleaned.length() > MAX_LENGTH) {
            cleaned = cleaned.substring(0, MAX_LENGTH);
            if (Character.isHighSurrogate(cleaned.charAt(cleaned.length() - 1))) {
                cleaned = cleaned.substring(0, cleaned.length() - 1);
            }
        }
        return cleaned;
    }
}
