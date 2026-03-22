package com.collabnotes.collabnotes.service.ot;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class OTOperationTransformTest {

    @Test
    void concurrentInsertAndDelete_convergesToSameDocument() {
        String base = "ABCDE";

        OTTextOperation left = OTTextOperation.builder("op-l", "left", 7)
                .retain(2)
                .insert("X")
                .retain(3)
                .build();

        OTTextOperation right = OTTextOperation.builder("op-r", "right", 7)
                .retain(1)
                .delete(2)
                .retain(2)
                .build();

        OTTransformPair transformed = OTOperationTransformer.transformConcurrent(left, right, true);

        String leftFirst = OTOperationApplier.apply(base, left);
        String finalFromLeftFirst = OTOperationApplier.apply(leftFirst, transformed.rightPrime());

        String rightFirst = OTOperationApplier.apply(base, right);
        String finalFromRightFirst = OTOperationApplier.apply(rightFirst, transformed.leftPrime());

        assertEquals(finalFromLeftFirst, finalFromRightFirst);
    }

    @Test
    void samePositionInsert_priorityControlsOrder() {
        String base = "ab";

        OTTextOperation left = OTTextOperation.builder("op-left", "left", 2)
                .retain(1)
                .insert("L")
                .retain(1)
                .build();

        OTTextOperation right = OTTextOperation.builder("op-right", "right", 2)
                .retain(1)
                .insert("R")
                .retain(1)
                .build();

        OTTransformPair leftPriority = OTOperationTransformer.transformConcurrent(left, right, true);
        OTTransformPair rightPriority = OTOperationTransformer.transformConcurrent(left, right, false);

        String leftPriorityDoc = OTOperationApplier.apply(
                OTOperationApplier.apply(base, left),
                leftPriority.rightPrime());

        String rightPriorityDoc = OTOperationApplier.apply(
                OTOperationApplier.apply(base, right),
                rightPriority.leftPrime());

        assertEquals("aLRb", leftPriorityDoc);
        assertEquals("aRLb", rightPriorityDoc);
    }

        @Test
        void samePositionInsert_defaultUsesLexicalPriority() {
                String base = "ab";

                OTTextOperation left = OTTextOperation.builder("op-z", "client-b", 2)
                                .retain(1)
                                .insert("L")
                                .retain(1)
                                .build();

                OTTextOperation right = OTTextOperation.builder("op-a", "client-a", 2)
                                .retain(1)
                                .insert("R")
                                .retain(1)
                                .build();

                OTTransformPair defaultPriority = OTOperationTransformer.transformConcurrent(left, right);

                String finalDoc = OTOperationApplier.apply(
                                OTOperationApplier.apply(base, right),
                                defaultPriority.leftPrime());

                assertEquals("aRLb", finalDoc);
        }
}
