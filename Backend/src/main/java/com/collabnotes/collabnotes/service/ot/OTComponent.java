package com.collabnotes.collabnotes.service.ot;

/**
 * Single operation component in a text OT operation.
 */
public sealed interface OTComponent permits OTDelete, OTInsert, OTRetain {
    int length();
}
