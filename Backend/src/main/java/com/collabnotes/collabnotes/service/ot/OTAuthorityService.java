package com.collabnotes.collabnotes.service.ot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Service;

/**
 * In-memory authority service for prosemirror-collab step sequencing.
 *
 * Each note has a monotonically increasing version. When a client submits
 * steps at the current server version, the steps are accepted and the version
 * advances by the number of steps. When the client is behind, the missing
 * steps are returned so the client can rebase.
 *
 * This service intentionally does NOT apply or interpret ProseMirror steps —
 * it treats them as opaque JSON blobs. The authoritative document lives in the
 * database (written by the debounced REST save path) and in each connected
 * client's in-memory ProseMirror state.
 *
 * State is session-scoped. A server restart clears all OT state; clients
 * re-initialise via the REST snapshot on reconnect.
 */
@Service
public class OTAuthorityService {

    private static final int MAX_STEP_HISTORY = 2000;

    private final ConcurrentHashMap<String, NoteOTState> noteStates = new ConcurrentHashMap<>();

    // ── Public API ──────────────────────────────────────────────────────────

    /**
     * Returns the current OT version for a note, initialising state if needed.
     */
    public int getVersion(String noteId) {
        return stateFor(noteId).version;
    }

    /**
     * Submit a batch of steps from a client.
     *
     * @param noteId        the note being edited
     * @param clientVersion the version the client's steps are based on
     * @param steps         opaque ProseMirror step JSON objects
     * @param clientId      ID of the submitting user (for broadcast attribution)
     * @return result indicating accepted (with new version) or catch-up needed
     */
    public OTSubmitResult submitSteps(String noteId, int clientVersion,
            List<Map<String, Object>> steps, String clientId) {

        if (steps == null || steps.isEmpty()) {
            return OTSubmitResult.error("steps must not be empty");
        }

        NoteOTState state = stateFor(noteId);

        synchronized (state) {
            if (clientVersion == state.version) {
                int newVersion = state.version + steps.size();
                state.addBatch(clientVersion, steps, clientId);
                state.version = newVersion;
                return OTSubmitResult.accepted(newVersion, steps, clientId);
            }

            if (clientVersion < state.version) {
                List<StepEntry> missing = state.stepsSince(clientVersion);
                return OTSubmitResult.catchUp(state.version, missing);
            }

            return OTSubmitResult.error("client version " + clientVersion
                    + " is ahead of server version " + state.version);
        }
    }

    /**
     * Returns all steps since the given version so a reconnecting client can
     * replay them. If there are no recorded steps at that version (e.g. after a
     * server restart) an empty list is returned and the client should re-bootstrap
     * from the REST snapshot at the current version.
     */
    public List<StepEntry> stepsSince(String noteId, int sinceVersion) {
        NoteOTState state = noteStates.get(noteId);
        if (state == null) return Collections.emptyList();
        synchronized (state) {
            return state.stepsSince(sinceVersion);
        }
    }

    /**
     * Removes OT state for a note. Call when the last subscriber leaves so
     * memory is not leaked between editing sessions.
     */
    public void clearNote(String noteId) {
        noteStates.remove(noteId);
    }

    // ── Internal ────────────────────────────────────────────────────────────

    private NoteOTState stateFor(String noteId) {
        return noteStates.computeIfAbsent(noteId, k -> new NoteOTState());
    }

    // ── Inner types ─────────────────────────────────────────────────────────

    private static final class NoteOTState {
        volatile int version = 0;
        final List<StepEntry> history = new ArrayList<>();

        void addBatch(int fromVersion, List<Map<String, Object>> steps, String clientId) {
            int v = fromVersion;
            for (Map<String, Object> step : steps) {
                history.add(new StepEntry(v, step, clientId));
                v++;
            }
            // Trim oldest entries to cap memory usage.
            while (history.size() > MAX_STEP_HISTORY) {
                history.remove(0);
            }
        }

        List<StepEntry> stepsSince(int sinceVersion) {
            List<StepEntry> result = new ArrayList<>();
            for (StepEntry entry : history) {
                if (entry.stepVersion() >= sinceVersion) {
                    result.add(entry);
                }
            }
            return Collections.unmodifiableList(result);
        }
    }

    /**
     * A single step stored in the authority history.
     *
     * @param stepVersion  the version index of this step (0-based)
     * @param step         opaque ProseMirror Step JSON
     * @param clientId     ID of the user who submitted this step
     */
    public record StepEntry(int stepVersion, Map<String, Object> step, String clientId) {}

    /**
     * Result of a {@link #submitSteps} call.
     */
    public sealed interface OTSubmitResult
            permits OTAuthorityService.Accepted,
                    OTAuthorityService.CatchUp,
                    OTAuthorityService.SubmitError {

        static OTSubmitResult accepted(int newVersion, List<Map<String, Object>> steps, String clientId) {
            return new Accepted(newVersion, steps, clientId);
        }

        static OTSubmitResult catchUp(int serverVersion, List<StepEntry> missing) {
            return new CatchUp(serverVersion, missing);
        }

        static OTSubmitResult error(String reason) {
            return new SubmitError(reason);
        }
    }

    public record Accepted(int newVersion, List<Map<String, Object>> steps, String clientId)
            implements OTSubmitResult {}

    public record CatchUp(int serverVersion, List<StepEntry> missing)
            implements OTSubmitResult {}

    public record SubmitError(String reason)
            implements OTSubmitResult {}
}
