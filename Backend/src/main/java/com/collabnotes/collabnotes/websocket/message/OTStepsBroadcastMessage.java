package com.collabnotes.collabnotes.websocket.message;

import java.util.List;
import java.util.Map;

/**
 * Server → All subscribers: broadcast of accepted steps.
 *
 * Sent to {@code /topic/notes/{noteId}/ot} after the authority accepts a
 * step submission. Every client (including the submitter) receives this so
 * they can advance their local version.
 *
 * The submitting client uses {@code clientId} to detect its own ack and
 * release any inflight steps from the pending queue.
 */
public class OTStepsBroadcastMessage {

    /** New server version after applying these steps. */
    private int version;

    /** The accepted step JSON objects. */
    private List<Map<String, Object>> steps;

    /** ID of the user who submitted these steps. */
    private String clientId;

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public List<Map<String, Object>> getSteps() { return steps; }
    public void setSteps(List<Map<String, Object>> steps) { this.steps = steps; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
}
