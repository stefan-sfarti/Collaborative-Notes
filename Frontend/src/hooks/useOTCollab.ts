import { collab, getVersion, receiveTransaction, sendableSteps } from "prosemirror-collab";
import { Step } from "prosemirror-transform";
import type { EditorView } from "prosemirror-view";
import { useCallback, useEffect, useRef } from "react";
import type { OTCatchUpDetail, OTStepsBroadcastDetail } from "../types";

export interface UseOTCollabParams {
  noteId: string | undefined;
  /** Called with the collab plugin so RichTextEditor can mount it. */
  onCollabPlugin: (plugin: ReturnType<typeof collab>) => void;
  /** Sends OT steps to the backend authority. */
  sendOTSteps: (noteId: string, version: number, steps: object[]) => Promise<boolean>;
  /**
   * Initial OT version received from the note-state WebSocket response.
   * `null` means the server version has not arrived yet — plugin creation is
   * deferred until a real version is known.
   */
  initialOtVersion: number | null;
  /**
   * Current user's ID — used as the prosemirror-collab clientID so the plugin
   * can recognise its own steps in server broadcasts (which tag steps with userId).
   */
  currentUserId: string | undefined;
}

export interface UseOTCollabReturn {
  /** Call this every time TipTap fires an onUpdate so the hook can flush steps. */
  onEditorUpdate: (view: EditorView) => void;
  /** Set by the hook; RichTextEditor should call this after mounting the editor. */
  setEditorView: (view: EditorView | null) => void;
}

/**
 * Manages the prosemirror-collab client-side OT state machine.
 *
 * Responsibilities:
 * - Provides the collab plugin initialised at the correct version.
 * - Listens to `ot-steps` (broadcast) and `ot-catchup` (private) DOM events
 *   and applies them via receiveTransaction.
 * - Flushes sendable steps after every local edit.
 * - Handles rebasing: on catch-up the pending inflight is retried after the
 *   missing steps are applied.
 */
export function useOTCollab({
  noteId,
  onCollabPlugin,
  sendOTSteps,
  initialOtVersion,
  currentUserId,
}: UseOTCollabParams): UseOTCollabReturn {
  const editorViewRef = useRef<EditorView | null>(null);
  // Wait for server round-trip before allowing another submit to avoid
  // overlapping payloads based on stale versions.
  const awaitingServerRef = useRef(false);
  // Prevent re-sending the exact same sendable payload before OT state advances.
  const lastPublishedKeyRef = useRef<string | null>(null);
  // Collab plugin instance — recreated when noteId or initialOtVersion changes.
  const collabPluginRef = useRef<ReturnType<typeof collab> | null>(null);

  // Initialise the collab plugin once the server OT version is known.
  // We defer until initialOtVersion is non-null so the plugin is created at
  // the correct server version rather than a placeholder 0.
  useEffect(() => {
    if (!noteId || initialOtVersion === null || !currentUserId) return;
    const plugin = collab({ version: initialOtVersion, clientID: currentUserId });
    collabPluginRef.current = plugin;
    onCollabPlugin(plugin);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [noteId, initialOtVersion, currentUserId]);

  // ── Flush sendable steps ──────────────────────────────────────────────────

  const getSafeSendableSteps = useCallback((view: EditorView) => {
    try {
      return sendableSteps(view.state);
    } catch {
      // Collab plugin is not attached to this editor state yet.
      return null;
    }
  }, []);

  const getSafeVersion = useCallback((view: EditorView): number | null => {
    try {
      return getVersion(view.state);
    } catch {
      // Collab plugin is not attached to this editor state yet.
      return null;
    }
  }, []);

  const flushSteps = useCallback(async (view: EditorView) => {
    if (!noteId || awaitingServerRef.current) return;

    const sendable = getSafeSendableSteps(view);
    if (!sendable) return;

    const stepsJson = sendable.steps.map((s) => s.toJSON() as object);
    const payloadKey = `${sendable.version}:${JSON.stringify(stepsJson)}`;
    if (payloadKey === lastPublishedKeyRef.current) {
      return;
    }

    const published = await sendOTSteps(noteId, sendable.version, stepsJson);
    if (published) {
      lastPublishedKeyRef.current = payloadKey;
      awaitingServerRef.current = true;
    }
  }, [noteId, sendOTSteps, getSafeSendableSteps]);

  const onEditorUpdate = useCallback((view: EditorView) => {
    void flushSteps(view).catch((error) => {
      console.error("[OT] Failed to flush steps:", error);
    });
  }, [flushSteps]);

  const setEditorView = useCallback((view: EditorView | null) => {
    editorViewRef.current = view;
  }, []);

  // ── Apply remote steps ────────────────────────────────────────────────────

  useEffect(() => {
    if (!noteId) return;

    const applySteps = (
      view: EditorView,
      steps: object[],
      clientIds: string[],
    ) => {
      try {
        const schema = view.state.schema;
        const pmSteps = steps.map((s) => Step.fromJSON(schema, s));
        const tr = receiveTransaction(view.state, pmSteps, clientIds);
        view.dispatch(tr);
      } catch (err) {
        console.error("[OT] Failed to apply remote steps:", err);
      }
    };

    const handleBroadcast = (e: CustomEvent<OTStepsBroadcastDetail>) => {
      const view = editorViewRef.current;
      if (!view) return;

      const { steps, clientId } = e.detail;
      // clientIds array must have one entry per step.
      const clientIds = steps.map(() => clientId);
      applySteps(view, steps, clientIds);

      // OT state advanced; allow future publish attempts.
      awaitingServerRef.current = false;
      lastPublishedKeyRef.current = null;

      // After applying remote steps there may be pending local steps to retry.
      flushSteps(view);
    };

    const handleCatchUp = (e: CustomEvent<OTCatchUpDetail>) => {
      const view = editorViewRef.current;
      if (!view) return;

      const { steps } = e.detail;
      if (steps.length === 0) {
        // Server has no history — client must re-bootstrap from the REST snapshot.
        // Dispatch a custom event so NoteEditor can trigger a hard reload.
        awaitingServerRef.current = false;
        lastPublishedKeyRef.current = null;
        window.dispatchEvent(new CustomEvent("ot-bootstrap-required"));
        return;
      }

      const rawSteps = steps.map((s) => s.step);
      const clientIds = steps.map((s) => s.clientId);
      applySteps(view, rawSteps, clientIds);

      // Retry our pending steps now that we've caught up.
      awaitingServerRef.current = false;
      lastPublishedKeyRef.current = null;
      flushSteps(view);
    };

    window.addEventListener("ot-steps", handleBroadcast as EventListener);
    window.addEventListener("ot-catchup", handleCatchUp as EventListener);

    return () => {
      window.removeEventListener("ot-steps", handleBroadcast as EventListener);
      window.removeEventListener("ot-catchup", handleCatchUp as EventListener);
    };
  }, [noteId, flushSteps]);

  // ── Sanity check: verify local version matches broadcast version ──────────

  useEffect(() => {
    if (!noteId) return;

    const handleBroadcastVersionCheck = (e: CustomEvent<OTStepsBroadcastDetail>) => {
      const view = editorViewRef.current;
      if (!view) return;

      const localVersion = getSafeVersion(view);
      if (localVersion === null) return;
      const expectedVersion = e.detail.version - e.detail.steps.length;
      if (localVersion !== expectedVersion) {
        // We are out of sync — request a resync via a dedicated event.
        // The NoteEditor can subscribe and trigger ot-resync WebSocket call.
        window.dispatchEvent(
          new CustomEvent("ot-version-mismatch", {
            detail: { localVersion, serverVersion: e.detail.version },
          }),
        );
      }
    };

    // This listener runs BEFORE handleBroadcast because it's registered earlier
    // in the same effect order — but since we need it after, we use capture phase.
    window.addEventListener("ot-steps", handleBroadcastVersionCheck as EventListener, true);
    return () => {
      window.removeEventListener("ot-steps", handleBroadcastVersionCheck as EventListener, true);
    };
  }, [noteId, getSafeVersion]);

  return { onEditorUpdate, setEditorView };
}
