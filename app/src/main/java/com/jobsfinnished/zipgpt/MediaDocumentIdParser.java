package com.jobsfinnished.zipgpt;

final class MediaDocumentIdParser {
    private MediaDocumentIdParser() {}

    static Long parseImageId(String documentId) {
        if (documentId == null) return null;
        int colon = documentId.indexOf(':');
        if (colon <= 0 || colon == documentId.length() - 1) return null;
        if (!"image".equals(documentId.substring(0, colon))) return null;
        String value = documentId.substring(colon + 1);
        if (!value.matches("[0-9]+")) return null;
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }
}
