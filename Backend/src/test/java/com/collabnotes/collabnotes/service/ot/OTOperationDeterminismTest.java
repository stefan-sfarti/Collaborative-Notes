package com.collabnotes.collabnotes.service.ot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class OTOperationDeterminismTest {

    @Test
    void transformConcurrent_sameInputs_sameOutputs() {
        OTTextOperation left = OTTextOperation.builder("left", "c1", 5)
                .retain(1)
                .insert("XY")
                .retain(2)
                .build();

        OTTextOperation right = OTTextOperation.builder("right", "c2", 5)
                .retain(1)
                .delete(1)
                .retain(1)
                .build();

        OTTransformPair first = OTOperationTransformer.transformConcurrent(left, right, true);
        OTTransformPair second = OTOperationTransformer.transformConcurrent(left, right, true);

        assertEquals(first, second);
    }

    @Test
    void composeSequential_sameInputs_sameOutput() {
        OTTextOperation first = OTTextOperation.builder("first", "u", 1)
                .retain(2)
                .insert("Z")
                .build();

        OTTextOperation second = OTTextOperation.builder("second", "u", 2)
                .delete(1)
                .retain(2)
                .build();

        OTTextOperation composed1 = OTOperationTransformer.composeSequential(first, second, "c1");
        OTTextOperation composed2 = OTOperationTransformer.composeSequential(first, second, "c1");

        assertEquals(composed1, composed2);
    }
}
