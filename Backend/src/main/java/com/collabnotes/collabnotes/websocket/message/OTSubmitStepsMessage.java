package com.collabnotes.collabnotes.websocket.message;

import java.util.List;
import java.util.Map;

/**
 * Client → Server: submit a batch of ProseMirror steps.
 *
 * Steps are treated as opaque JSON objects by the backend; the structure is
 * defined by the prosemirror-transform library on the client.
 */
public class OTSubmitStepsMessage {

    /** The OT version the client's document is currently at. */
    private int version;

    /** Opaque ProseMirror Step JSON objects. */
    private List<Map<String, Object>> steps;

    /** The submitting user's ID (forwarded from JWT, not trusted from body). */
    private String clientId;

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public List<Map<String, Object>> getSteps() { return steps; }
    public void setSteps(List<Map<String, Object>> steps) { this.steps = steps; }

    public String getClientId() { return clientId; }
    public void setClientId(String clientId) { this.clientId = clientId; }
}
