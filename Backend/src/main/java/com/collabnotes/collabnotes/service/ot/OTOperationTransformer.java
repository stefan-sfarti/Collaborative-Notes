package com.collabnotes.collabnotes.service.ot;

/**
 * Deterministic transform and compose operations for plain-text OT.
 */
public final class OTOperationTransformer {
    private OTOperationTransformer() {
    }

    public static OTTransformPair transformConcurrent(OTTextOperation left, OTTextOperation right) {
        boolean leftHasInsertPriority = hasLexicalPriority(left, right);
        return transformConcurrent(left, right, leftHasInsertPriority);
    }

    public static OTTransformPair transformConcurrent(
            OTTextOperation left,
            OTTextOperation right,
            boolean leftHasInsertPriority) {
        OTOperationValidator.validateConcurrent(left, right);

        OTTextOperation.Builder leftPrime = OTTextOperation.builder(
                left.operationId() + "-prime", left.clientId(), left.baseRevision() + 1);
        OTTextOperation.Builder rightPrime = OTTextOperation.builder(
                right.operationId() + "-prime", right.clientId(), right.baseRevision() + 1);

        OTCursor lc = new OTCursor(left);
        OTCursor rc = new OTCursor(right);

        while (lc.hasMore() || rc.hasMore()) {
            boolean handled = handleTransformInsertBranch(lc, rc, leftPrime, rightPrime, leftHasInsertPriority);
            if (!handled) {
                handleTransformNonInsertBranch(lc, rc, leftPrime, rightPrime);
            }
        }

        return new OTTransformPair(leftPrime.build(), rightPrime.build());
    }

    public static OTTextOperation composeSequential(OTTextOperation first, OTTextOperation second, String newOperationId) {
        OTOperationValidator.validateComposable(first, second);

        OTTextOperation.Builder composed = OTTextOperation.builder(
                newOperationId, first.clientId(), first.baseRevision());

        OTCursor fc = new OTCursor(first);
        OTCursor sc = new OTCursor(second);

        while (fc.hasMore() || sc.hasMore()) {
            boolean handled = handleComposeDirectBranch(fc, sc, composed);
            if (!handled) {
                handleComposePairedBranch(fc, sc, composed);
            }
        }

        return composed.build();
    }

    private static boolean handleTransformInsertBranch(
            OTCursor lc,
            OTCursor rc,
            OTTextOperation.Builder leftPrime,
            OTTextOperation.Builder rightPrime,
            boolean leftHasInsertPriority) {
        if (lc.hasMore() && lc.current() instanceof OTInsert(String leftText)
                && (!rc.hasMore() || !(rc.current() instanceof OTInsert) || leftHasInsertPriority)) {
            int len = leftText.length();
            lc.take(len);
            leftPrime.insert(leftText);
            rightPrime.retain(len);
            return true;
        }

        if (rc.hasMore() && rc.current() instanceof OTInsert(String rightText)) {
            int len = rightText.length();
            rc.take(len);
            leftPrime.retain(len);
            rightPrime.insert(rightText);
            return true;
        }

        return false;
    }

    private static void handleTransformNonInsertBranch(
            OTCursor lc,
            OTCursor rc,
            OTTextOperation.Builder leftPrime,
            OTTextOperation.Builder rightPrime) {
        if (!lc.hasMore() || !rc.hasMore()) {
            throw new IllegalStateException("Malformed operations for concurrent transform");
        }

        int takeLen = Math.min(lc.remaining(), rc.remaining());
        OTComponent ls = lc.take(takeLen);
        OTComponent rs = rc.take(takeLen);

        if (ls instanceof OTRetain && rs instanceof OTRetain) {
            leftPrime.retain(takeLen);
            rightPrime.retain(takeLen);
        } else if (ls instanceof OTDelete && rs instanceof OTRetain) {
            leftPrime.delete(takeLen);
        } else if (ls instanceof OTRetain && rs instanceof OTDelete) {
            rightPrime.delete(takeLen);
        } else if (!(ls instanceof OTDelete && rs instanceof OTDelete)) {
            throw new IllegalStateException("Unexpected component pair");
        }
    }

    private static boolean handleComposeDirectBranch(
            OTCursor fc,
            OTCursor sc,
            OTTextOperation.Builder composed) {
        if (sc.hasMore() && sc.current() instanceof OTInsert(String insertText)) {
            sc.take(insertText.length());
            composed.insert(insertText);
            return true;
        }

        if (fc.hasMore() && fc.current() instanceof OTDelete(int deleteCount)) {
            fc.take(deleteCount);
            composed.delete(deleteCount);
            return true;
        }

        return false;
    }

    private static void handleComposePairedBranch(
            OTCursor fc,
            OTCursor sc,
            OTTextOperation.Builder composed) {
        if (!fc.hasMore() || !sc.hasMore()) {
            throw new IllegalStateException("Malformed operations for compose");
        }

        int takeLen = Math.min(fc.remaining(), sc.remaining());
        OTComponent fs = fc.take(takeLen);
        OTComponent ss = sc.take(takeLen);

        if (fs instanceof OTRetain && ss instanceof OTRetain) {
            composed.retain(takeLen);
        } else if (fs instanceof OTRetain && ss instanceof OTDelete) {
            composed.delete(takeLen);
        } else if (fs instanceof OTInsert(String inserted) && ss instanceof OTRetain) {
            composed.insert(inserted);
        } else if (!(fs instanceof OTInsert && ss instanceof OTDelete)) {
            throw new IllegalStateException("Unexpected component pair in compose");
        }
    }

    private static boolean hasLexicalPriority(OTTextOperation left, OTTextOperation right) {
        int clientCompare = left.clientId().compareTo(right.clientId());
        if (clientCompare != 0) {
            return clientCompare < 0;
        }
        return left.operationId().compareTo(right.operationId()) <= 0;
    }
}
