package com.collabnotes.collabnotes.service.ot;

/**
 * Applies validated OT operations against a document string.
 */
public final class OTOperationApplier {
    private OTOperationApplier() {
    }

    public static String apply(String document, OTTextOperation operation) {
        if (document == null) {
            throw new IllegalArgumentException("document is required");
        }
        OTOperationValidator.validateForDocument(operation, document.length());

        StringBuilder out = new StringBuilder(operation.targetLength());
        int cursor = 0;

        for (OTComponent component : operation.components()) {
            if (component instanceof OTRetain(int count)) {
                out.append(document, cursor, cursor + count);
                cursor += count;
            } else if (component instanceof OTDelete(int count)) {
                cursor += count;
            } else if (component instanceof OTInsert(String text)) {
                out.append(text);
            }
        }

        return out.toString();
    }
}
