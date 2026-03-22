package com.collabnotes.collabnotes.websocket.message;

import java.util.List;
import java.util.Map;

/**
 * Server → Client: catch-up payload sent when the client is behind.
 *
 * Sent to {@code /user/queue/notes/{noteId}/ot-catchup} when a client submits
 * steps at a stale version. The client should apply these missing steps via
 * {@code receiveTransaction} before retrying its submission.
 *
 * If {@code steps} is empty, the client is so far behind that it must
 * re-bootstrap from the REST snapshot at {@code version}.
 */
public class OTCatchUpMessage {

    /** Current server version. */
    private int version;

    /**
     * Missing steps since the client's version.
     * Each entry carries the step JSON plus the {@code clientId} of the original
     * submitter (needed by {@code receiveTransaction}).
     */
    private List<StepWithClient> steps;

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public List<StepWithClient> getSteps() { return steps; }
    public void setSteps(List<StepWithClient> steps) { this.steps = steps; }

    public static class StepWithClient {
        private Map<String, Object> step;
        private String clientId;

        public StepWithClient() {}
        public StepWithClient(Map<String, Object> step, String clientId) {
            this.step = step;
            this.clientId = clientId;
        }

        public Map<String, Object> getStep() { return step; }
        public void setStep(Map<String, Object> step) { this.step = step; }

        public String getClientId() { return clientId; }
        public void setClientId(String clientId) { this.clientId = clientId; }
    }
}
