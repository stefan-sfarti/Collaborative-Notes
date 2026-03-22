package com.collabnotes.collabnotes.service.ot;

/**
 * Result of apply-once execution against an idempotency ledger.
 */
public record OTApplyResult(String document, boolean applied) {
}
