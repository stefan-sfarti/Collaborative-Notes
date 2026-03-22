package com.collabnotes.collabnotes.service.ot;

/**
 * Pair of transformed operations for concurrent application.
 */
public record OTTransformPair(OTTextOperation leftPrime, OTTextOperation rightPrime) {
}
