import type { Plugin } from "prosemirror-state";
import { useEffect, useRef, useState } from "react";
import { useParams } from "react-router-dom";
import ActiveUsersList from "../components/ActiveUsersList";
import CollaboratorsList from "../components/CollaboratorsList";
import EditorHeader from "../components/EditorHeader";
import RichTextEditor from "../components/RichTextEditor";
import { useAuth } from "../contexts/AuthContext";
import { useActiveUsers } from "../hooks/useActiveUsers";
import { useCollaborationEvents } from "../hooks/useCollaborationEvents";
import { useNoteFetch } from "../hooks/useNoteFetch";
import { useOTCollab } from "../hooks/useOTCollab";
import { useSaveManager } from "../hooks/useSaveManager";
import { useWebSocket } from "../services/WebSocketProvider";
import type { NoteStateEventDetail } from "../types";

function NoteEditor() {
  const { noteId } = useParams<{ noteId: string }>();
  const { currentUser, getFreshToken } = useAuth();

  const {
    connectionStatus,
    subscribeToNote,
    unsubscribeFromNote,
    sendTypingIndicator,
    sendOTSteps,
    getStompClient,
  } = useWebSocket();

  const {
    setActiveUsersState,
    fetchedUserDetails,
    lookupUserById,
    activeUsersForList,
  } = useActiveUsers();

  const {
    note,
    localTitle,
    localContent,
    collaborators,
    isOwner,
    owner,
    loading,
    error,
    setNote,
    setLocalTitle,
    setLocalContent,
    setCollaborators,
    setError,
    lastSavedContentRef,
  } = useNoteFetch({ noteId, currentUser, lookupUserById });

  // ── OT version tracking ─────────────────────────────────────────────────
  // Starts as null so useOTCollab defers plugin creation until the real server
  // version arrives via the note-state WebSocket response.
  const [otVersion, setOtVersion] = useState<number | null>(null);
  const [collabPlugin, setCollabPlugin] = useState<Plugin | null>(null);

  // Listen for the note-state event to extract otVersion before useCollaborationEvents
  // processes it (so the plugin is initialised with the correct version).
  useEffect(() => {
    const handler = (e: CustomEvent<NoteStateEventDetail>) => {
      if (typeof e.detail.otVersion === "number") {
        setOtVersion(e.detail.otVersion);
      }
    };
    window.addEventListener("note-state", handler as EventListener);
    return () => window.removeEventListener("note-state", handler as EventListener);
  }, []);

  const { onEditorUpdate, setEditorView } = useOTCollab({
    noteId,
    onCollabPlugin: setCollabPlugin,
    sendOTSteps,
    initialOtVersion: otVersion,
    currentUserId: currentUser?.id,
  });

  const { saving, saveError, isNoteModified, handleForceSave, handleChange } =
    useSaveManager({
      noteId,
      note,
      localTitle,
      localContent,
      lastSavedContentRef,
      setLocalTitle,
      setLocalContent,
      setNote,
      sendTypingIndicator,
    });

  useCollaborationEvents({
    noteId,
    currentUser,
    isOTActive: otVersion !== null,
    setNote,
    setLocalTitle,
    setLocalContent,
    lastSavedContentRef,
    setActiveUsersState,
    setCollaborators,
    lookupUserById,
  });

  // Subscribe/unsubscribe note channel exactly once per active note connection.
  useEffect(() => {
    if (connectionStatus !== "connected" || !noteId || !currentUser?.id) return;

    subscribeToNote(noteId);

    return () => {
      unsubscribeFromNote(noteId);
    };
  }, [
    connectionStatus,
    noteId,
    currentUser?.id,
    subscribeToNote,
    unsubscribeFromNote,
  ]);

  // Handle bootstrap-required event: OT state is stale (server restart),
  // reset OT version to 0 so the collab plugin is re-initialised from REST content.
  const bootstrapHandledRef = useRef(false);
  useEffect(() => {
    bootstrapHandledRef.current = false;
    const handler = () => {
      if (bootstrapHandledRef.current) return;
      bootstrapHandledRef.current = true;
      setOtVersion(null);
      setCollabPlugin(null);
    };
    window.addEventListener("ot-bootstrap-required", handler);
    return () => window.removeEventListener("ot-bootstrap-required", handler);
  }, [noteId]);

  // If client/server OT versions diverge, ask the server for a targeted catch-up.
  // This avoids requiring a full page refresh when an event is dropped.
  const lastResyncRequestedVersionRef = useRef<number | null>(null);
  useEffect(() => {
    if (!noteId) return;

    const requestResync = async (localVersion: number) => {
      if (lastResyncRequestedVersionRef.current === localVersion) {
        return;
      }

      const client = getStompClient();
      if (!client || !client.connected) return;

      const token = await getFreshToken();
      if (!token) return;

      lastResyncRequestedVersionRef.current = localVersion;
      client.publish({
        destination: `/app/notes/${noteId}/ot-resync`,
        headers: {
          Authorization: `Bearer ${token}`,
          "content-type": "application/json",
        },
        body: JSON.stringify({ version: localVersion, steps: [] }),
      });
    };

    const handleMismatch: EventListener = (event) => {
      const customEvent = event as CustomEvent<{ localVersion: number; serverVersion: number }>;
      void requestResync(customEvent.detail.localVersion);
    };

    window.addEventListener("ot-version-mismatch", handleMismatch);
    return () => {
      window.removeEventListener("ot-version-mismatch", handleMismatch);
    };
  }, [noteId, getStompClient, getFreshToken]);

  useEffect(() => {
    lastResyncRequestedVersionRef.current = null;
  }, [noteId]);

  return (
    <div className="flex flex-col min-h-[100dvh] bg-base-200">
      <EditorHeader
        isOwner={isOwner}
        saving={saving}
        isNoteModified={isNoteModified}
        connectionStatus={connectionStatus}
        onForceSave={handleForceSave}
        saveError={saveError}
      />

      {error && (
        <div className="alert alert-error m-3" onClick={() => setError("")}>
          <span>{error}</span>
        </div>
      )}

      {loading ? (
        <div className="flex flex-1 items-center justify-center">
          <span className="loading loading-spinner loading-lg" />
        </div>
      ) : (
        <div className="flex flex-1 flex-col lg:flex-row min-h-0">
          <RichTextEditor
            key={collabPlugin ? "ot-active" : "no-ot"}
            title={localTitle}
            content={localContent}
            onChange={handleChange}
            collabPlugin={collabPlugin}
            onEditorUpdate={onEditorUpdate}
            onEditorViewReady={setEditorView}
          />

          <aside className="w-full lg:w-80 lg:min-w-80 border-t lg:border-t-0 lg:border-l border-base-300 bg-base-100 px-3 sm:px-4 py-3 sm:py-4 flex flex-col gap-3 max-h-[45vh] lg:max-h-none overflow-y-auto overflow-x-hidden">
            <ActiveUsersList
              activeUsers={activeUsersForList}
              currentUser={currentUser}
            />

            <CollaboratorsList
              noteId={noteId}
              collaborators={collaborators}
              owner={owner}
              onCollaboratorChange={setCollaborators}
              isOwner={isOwner}
              fetchedUserDetails={fetchedUserDetails}
              lookupUserById={lookupUserById}
            />
          </aside>
        </div>
      )}
    </div>
  );
}

export default NoteEditor;
