package com.collabnotes.collabnotes.service.ot;

/**
 * Validation utilities for OT operations.
 */
public final class OTOperationValidator {
    private OTOperationValidator() {
    }

    public static void validateForDocument(OTTextOperation operation, int documentLength) {
        if (operation == null) {
            throw new IllegalArgumentException("operation is required");
        }
        if (documentLength < 0) {
            throw new IllegalArgumentException("documentLength must be >= 0");
        }
        if (operation.baseLength() != documentLength) {
            throw new IllegalArgumentException(
                    "Operation baseLength " + operation.baseLength() + " does not match document length "
                            + documentLength);
        }
    }

    public static void validateComposable(OTTextOperation first, OTTextOperation second) {
        if (first == null || second == null) {
            throw new IllegalArgumentException("Both operations are required");
        }
        if (first.targetLength() != second.baseLength()) {
            throw new IllegalArgumentException("first.targetLength must equal second.baseLength");
        }
    }

    public static void validateConcurrent(OTTextOperation left, OTTextOperation right) {
        if (left == null || right == null) {
            throw new IllegalArgumentException("Both operations are required");
        }
        if (left.baseLength() != right.baseLength()) {
            throw new IllegalArgumentException("Concurrent operations must have the same baseLength");
        }
        if (left.baseRevision() != right.baseRevision()) {
            throw new IllegalArgumentException("Concurrent operations must have the same baseRevision");
        }
    }
}
