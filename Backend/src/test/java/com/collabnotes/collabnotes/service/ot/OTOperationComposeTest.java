package com.collabnotes.collabnotes.service.ot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class OTOperationComposeTest {

    @Test
    void composeSequential_matchesStepByStepApplication() {
        String base = "abcde";

        OTTextOperation first = OTTextOperation.builder("first", "u1", 1)
                .retain(2)
                .insert("X")
                .retain(3)
                .build();

        OTTextOperation second = OTTextOperation.builder("second", "u1", 2)
                .retain(1)
                .delete(3)
                .retain(2)
                .build();

        String stepByStep = OTOperationApplier.apply(OTOperationApplier.apply(base, first), second);

        OTTextOperation composed = OTOperationTransformer.composeSequential(first, second, "composed");
        String composedResult = OTOperationApplier.apply(base, composed);

        assertEquals(stepByStep, composedResult);
    }
}
