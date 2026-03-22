package com.collabnotes.collabnotes.service.ot;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * In-memory operation ledger for duplicate operation suppression.
 */
public final class OTOperationLedger {
    private static final int DEFAULT_MAX_ENTRIES = 10_000;

    private final Map<LedgerKey, String> appliedResults;

    public OTOperationLedger() {
        this(DEFAULT_MAX_ENTRIES);
    }

    public OTOperationLedger(int maxEntries) {
        if (maxEntries <= 0) {
            throw new IllegalArgumentException("maxEntries must be > 0");
        }
        this.appliedResults = new LinkedHashMap<>(maxEntries + 1, 0.75f, true) {
            @Override
            protected boolean removeEldestEntry(Map.Entry<LedgerKey, String> eldest) {
                return size() > maxEntries;
            }
        };
    }

    public synchronized OTApplyResult applyOnce(String noteId, String document, OTTextOperation operation) {
        if (noteId == null || noteId.isBlank()) {
            throw new IllegalArgumentException("noteId is required");
        }
        if (operation == null) {
            throw new IllegalArgumentException("operation is required");
        }

        LedgerKey key = new LedgerKey(noteId, operation.clientId(), operation.operationId());
        String previousResult = appliedResults.get(key);
        if (previousResult != null) {
            return new OTApplyResult(previousResult, false);
        }

        String updated = OTOperationApplier.apply(document, operation);
        appliedResults.put(key, updated);
        return new OTApplyResult(updated, true);
    }

    private record LedgerKey(String noteId, String clientId, String operationId) {
    }
}
