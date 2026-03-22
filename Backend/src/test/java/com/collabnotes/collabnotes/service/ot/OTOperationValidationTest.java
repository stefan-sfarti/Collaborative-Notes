package com.collabnotes.collabnotes.service.ot;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class OTOperationValidationTest {

    @Test
    void validateForDocument_rejectsMismatchedBaseLength() {
        OTTextOperation operation = OTTextOperation.builder("op1", "client-1", 0)
                .retain(3)
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> OTOperationValidator.validateForDocument(operation, 2));
    }

    @Test
    void validateConcurrent_rejectsRevisionMismatch() {
        OTTextOperation left = OTTextOperation.builder("left", "c1", 1)
                .retain(2)
                .build();
        OTTextOperation right = OTTextOperation.builder("right", "c2", 2)
                .retain(2)
                .build();

        assertThrows(IllegalArgumentException.class,
                () -> OTOperationValidator.validateConcurrent(left, right));
    }

    @Test
    void validateComposable_acceptsMatchingLengths() {
        OTTextOperation first = OTTextOperation.builder("first", "u", 0)
                .retain(1)
                .insert("x")
                .build();
        OTTextOperation second = OTTextOperation.builder("second", "u", 1)
                .retain(2)
                .build();

        assertDoesNotThrow(() -> OTOperationValidator.validateComposable(first, second));
    }

    @Test
    void builder_rejectsInvalidComponents() {
        assertThrows(IllegalArgumentException.class, this::buildWithInvalidRetain);
        assertThrows(IllegalArgumentException.class, this::buildWithInvalidDelete);
        assertThrows(IllegalArgumentException.class, this::buildWithInvalidInsert);
    }

    private void buildWithInvalidRetain() {
        OTTextOperation.builder("o1", "u", 0).retain(0);
    }

    private void buildWithInvalidDelete() {
        OTTextOperation.builder("o1", "u", 0).delete(0);
    }

    private void buildWithInvalidInsert() {
        OTTextOperation.builder("o1", "u", 0).insert("");
    }
}
