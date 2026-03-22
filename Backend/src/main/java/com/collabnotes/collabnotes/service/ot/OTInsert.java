package com.collabnotes.collabnotes.service.ot;

/**
 * Insert text at the current position.
 */
public record OTInsert(String text) implements OTComponent {
    public OTInsert {
        if (text == null || text.isEmpty()) {
            throw new IllegalArgumentException("Insert text must be non-empty");
        }
    }

    @Override
    public int length() {
        return text.length();
    }
}
