package com.collabnotes.collabnotes.service.ot;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class OTOperationIdempotencyTest {

    @Test
    void ledger_applyOnce_dropsDuplicateOperationId() {
        OTOperationLedger ledger = new OTOperationLedger();

        OTTextOperation operation = OTTextOperation.builder("dup-op", "u1", 0)
                .retain(1)
                .insert("X")
                .retain(1)
                .build();

        OTApplyResult first = ledger.applyOnce("note-1", "ab", operation);
        OTApplyResult second = ledger.applyOnce("note-1", "ab", operation);

        assertTrue(first.applied());
        assertFalse(second.applied());
        assertEquals("aXb", first.document());
        assertEquals("aXb", second.document());
    }

        @Test
        void ledger_sameOperationIdDifferentScope_isNotDropped() {
        OTOperationLedger ledger = new OTOperationLedger();

        OTTextOperation opA = OTTextOperation.builder("same-id", "u1", 0)
            .insert("A")
            .retain(1)
            .build();
        OTTextOperation opB = OTTextOperation.builder("same-id", "u2", 0)
            .insert("B")
            .retain(1)
            .build();

        OTApplyResult first = ledger.applyOnce("note-1", "x", opA);
        OTApplyResult second = ledger.applyOnce("note-2", "x", opB);

        assertTrue(first.applied());
        assertTrue(second.applied());
        assertEquals("Ax", first.document());
        assertEquals("Bx", second.document());
        }

    @Test
    void ledger_evictsOldestWhenCapacityExceeded() {
        OTOperationLedger ledger = new OTOperationLedger(2);

        OTTextOperation op1 = OTTextOperation.builder("op1", "u1", 0)
                .insert("1")
                .build();
        OTTextOperation op2 = OTTextOperation.builder("op2", "u1", 0)
                .insert("2")
                .build();
        OTTextOperation op3 = OTTextOperation.builder("op3", "u1", 0)
                .insert("3")
                .build();

        assertTrue(ledger.applyOnce("note-1", "", op1).applied());
        assertTrue(ledger.applyOnce("note-1", "", op2).applied());
        assertTrue(ledger.applyOnce("note-1", "", op3).applied());

        OTApplyResult replay = ledger.applyOnce("note-1", "", op1);
        assertTrue(replay.applied());
        assertEquals("1", replay.document());
    }
}
