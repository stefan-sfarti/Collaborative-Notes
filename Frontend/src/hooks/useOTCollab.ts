import { useEffect, useRef, useCallback } from "react";
import { collab, sendableSteps, getVersion, receiveTransaction } from "prosemirror-collab";
import { Step } from "prosemirror-transform";
import type { EditorView } from "prosemirror-view";
import type { OTStepsBroadcastDetail, OTCatchUpDetail } from "../types";

export interface UseOTCollabParams {
  noteId: string | undefined;
  /** Called with the collab plugin so RichTextEditor can mount it. */
  onCollabPlugin: (plugin: ReturnType<typeof collab>) => void;
  /** Sends OT steps to the backend authority. */
  sendOTSteps: (noteId: string, version: number, steps: object[]) => Promise<void>;
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
  // Track whether a send is already in-flight to avoid double-submission.
  const sendingRef = useRef(false);
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

  const flushSteps = useCallback(async (view: EditorView) => {
    if (!noteId || sendingRef.current) return;

    const sendable = sendableSteps(view.state);
    if (!sendable) return;

    sendingRef.current = true;
    try {
      await sendOTSteps(
        noteId,
        sendable.version,
        sendable.steps.map((s) => s.toJSON() as object),
      );
    } finally {
      sendingRef.current = false;
    }
  }, [noteId, sendOTSteps]);

  const onEditorUpdate = useCallback((view: EditorView) => {
    flushSteps(view);
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
        window.dispatchEvent(new CustomEvent("ot-bootstrap-required"));
        return;
      }

      const rawSteps = steps.map((s) => s.step);
      const clientIds = steps.map((s) => s.clientId);
      applySteps(view, rawSteps, clientIds);

      // Retry our pending steps now that we've caught up.
      sendingRef.current = false;
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

      const localVersion = getVersion(view.state);
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
  }, [noteId]);

  return { onEditorUpdate, setEditorView };
}
