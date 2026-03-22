package com.collabnotes.collabnotes.service.ot;

/**
 * Mutable split cursor for component iteration.
 */
final class OTCursor {
    private final OTTextOperation operation;
    private int componentIndex;
    private int offset;

    OTCursor(OTTextOperation operation) {
        this.operation = operation;
    }

    boolean hasMore() {
        return componentIndex < operation.components().size();
    }

    OTComponent current() {
        return operation.components().get(componentIndex);
    }

    int remaining() {
        return current().length() - offset;
    }

    OTComponent take(int length) {
        OTComponent c = current();
        if (length <= 0 || length > remaining()) {
            throw new IllegalArgumentException("Invalid segment length: " + length);
        }

        OTComponent segment;
        if (c instanceof OTRetain) {
            segment = new OTRetain(length);
        } else if (c instanceof OTDelete) {
            segment = new OTDelete(length);
        } else if (c instanceof OTInsert insert) {
            segment = new OTInsert(insert.text().substring(offset, offset + length));
        } else {
            throw new IllegalStateException("Unsupported component type");
        }

        offset += length;
        if (offset == c.length()) {
            componentIndex++;
            offset = 0;
        }
        return segment;
    }
}
