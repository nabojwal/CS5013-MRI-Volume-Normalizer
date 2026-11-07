package org.cbihi.mrinormalizer.application.provenance.manifest;

import java.util.Locale;

/** A canonical logical reference; it neither resolves a path nor proves containment. */
public record RelativePath(Root root, String path) {

    public enum Root { SOURCE, OUTPUT }

    public RelativePath {
        if (root == null || path == null) {
            throw new IllegalArgumentException("Relative path components must be non-null");
        }
        if (path.isEmpty() || path.length() > 4096 || path.startsWith("/") || path.endsWith("/")) {
            throw new IllegalArgumentException("Relative path must be canonical and portable");
        }

        // Count UTF-8 widths directly, without allocating or exposing a byte payload.
        int utf8Length = 0;
        for (int offset = 0; offset < path.length();) {
            char current = path.charAt(offset);
            if (Character.isLowSurrogate(current)
                    || (Character.isHighSurrogate(current)
                            && (offset + 1 == path.length() || !Character.isLowSurrogate(path.charAt(offset + 1))))) {
                throw new IllegalArgumentException("Relative path must be canonical and portable");
            }
            int scalar = path.codePointAt(offset);
            if (Character.isISOControl(scalar) || scalar == '\\' || scalar == ':') {
                throw new IllegalArgumentException("Relative path must be canonical and portable");
            }
            utf8Length += scalar <= 0x7f ? 1 : scalar <= 0x7ff ? 2 : scalar <= 0xffff ? 3 : 4;
            if (utf8Length > 4096) {
                throw new IllegalArgumentException("Relative path must be canonical and portable");
            }
            offset += Character.charCount(scalar);
        }

        for (var segment : path.split("/", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")
                    || segment.endsWith(".") || segment.endsWith(" ")) {
                throw new IllegalArgumentException("Relative path must be canonical and portable");
            }
            int dot = segment.indexOf('.');
            var base = (dot < 0 ? segment : segment.substring(0, dot)).toUpperCase(Locale.ROOT);
            boolean reserved = switch (base) {
                case "CON", "PRN", "AUX", "NUL", "CONIN$", "CONOUT$" -> true;
                default -> base.matches("(?:COM|LPT)[1-9¹²³]");
            };
            if (reserved) {
                throw new IllegalArgumentException("Relative path must be canonical and portable");
            }
        }
    }
}
