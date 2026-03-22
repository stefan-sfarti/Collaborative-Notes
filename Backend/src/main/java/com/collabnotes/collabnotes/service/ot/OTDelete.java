package com.collabnotes.collabnotes.service.ot;

/**
 * Delete the next N characters.
 */
public record OTDelete(int count) implements OTComponent {
    public OTDelete {
        if (count <= 0) {
            throw new IllegalArgumentException("Delete count must be > 0");
        }
    }

    @Override
    public int length() {
        return count;
    }
}
