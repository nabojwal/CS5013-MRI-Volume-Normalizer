package org.cbihi.mrinormalizer.application.dataset.model;

/**
 * Exact, restricted SOURCE-relative discovered spelling for F2 diagnostics only.
 * This is not an F1 RelativePath, containment proof, execution reference or
 * provenance reference, and it has no filesystem or I/O capability.
 *
 * The value is nonempty and bounded to 4096 UTF-16 units with valid Unicode
 * scalars. Slash separates discovered segments. Interior literal backslashes
 * are preserved, but both separator spellings are checked against empty, dot
 * and traversal segments. Rooted and drive-rooted spellings
 * are rejected, as are controls. Validation never normalizes or repairs a name.
 * Windows reserved names, trailing dots/spaces and literal colons or
 * backslashes may identify entries rejected by F1 portability rules.
 * Colon-containing names are not interpreted as URI schemes or drive-relative
 * references; this value remains diagnostic spelling only.
 *
 * Only a discovered spelling belongs here, never an exception, provider
 * description or error message. Syntax validation does not attest discovery
 * or containment; this restricted diagnostic value must not authorize I/O.
 */
public record InventoryLocation(String spelling) {
    public InventoryLocation {
        if (spelling == null || spelling.isEmpty() || spelling.length() > 4096
                || spelling.startsWith("/") || spelling.startsWith("\\")
                || spelling.length() >= 3
                && (spelling.charAt(0) >= 'A' && spelling.charAt(0) <= 'Z'
                    || spelling.charAt(0) >= 'a' && spelling.charAt(0) <= 'z')
                && spelling.charAt(1) == ':'
                && (spelling.charAt(2) == '/' || spelling.charAt(2) == '\\')) {
            throw new IllegalArgumentException("Inventory location must be a bounded relative filesystem spelling");
        }
        for (int offset = 0; offset < spelling.length();) {
            char current = spelling.charAt(offset);
            if (Character.isLowSurrogate(current)
                    || Character.isHighSurrogate(current)
                    && (offset + 1 == spelling.length() || !Character.isLowSurrogate(spelling.charAt(offset + 1)))) {
                throw new IllegalArgumentException("Inventory location must be a bounded relative filesystem spelling");
            }
            int scalar = spelling.codePointAt(offset);
            if (Character.isISOControl(scalar)) {
                throw new IllegalArgumentException("Inventory location must be a bounded relative filesystem spelling");
            }
            offset += Character.charCount(scalar);
        }
        for (String segment : spelling.split("[/\\\\]", -1)) {
            if (segment.isEmpty() || segment.equals(".") || segment.equals("..")) {
                throw new IllegalArgumentException("Inventory location must be a bounded relative filesystem spelling");
            }
        }
    }
}
