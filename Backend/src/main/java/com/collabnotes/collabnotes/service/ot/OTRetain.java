package com.collabnotes.collabnotes.service.ot;

/**
 * Keep the next N characters unchanged.
 */
public record OTRetain(int count) implements OTComponent {
    public OTRetain {
        if (count <= 0) {
            throw new IllegalArgumentException("Retain count must be > 0");
        }
    }

    @Override
    public int length() {
        return count;
    }
}
