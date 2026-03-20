// src/pages/NoteEditor.jsx
import React, { useEffect } from "react";
import { useParams } from "react-router-dom";
import { useAuth } from "../contexts/AuthContext";
import ActiveUsersList from "../components/ActiveUsersList";
import CollaboratorsList from "../components/CollaboratorsList";
import EditorHeader from "../components/EditorHeader";
import RichTextEditor from "../components/RichTextEditor";
import { useWebSocket } from "../services/WebSocketProvider.jsx";
import { useActiveUsers } from "../hooks/useActiveUsers";
import { useNoteFetch } from "../hooks/useNoteFetch";
import { useSaveManager } from "../hooks/useSaveManager";
import { useCollaborationEvents } from "../hooks/useCollaborationEvents";

function NoteEditor() {
  const { noteId } = useParams();
  const { currentUser } = useAuth();

  const {
    connectionStatus,
    subscribeToNote,
    unsubscribeFromNote,
    sendNoteUpdate,
    sendTypingIndicator,
  } = useWebSocket();

  const {
    activeUsersState,
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

  const { saving, saveError, isNoteModified, handleForceSave, handleChange } =
    useSaveManager({
      noteId,
      note,
      localTitle,
      localContent,
      lastSavedContentRef,
      sendNoteUpdate,
      setLocalTitle,
      setLocalContent,
      sendTypingIndicator,
    });

  useCollaborationEvents({
    noteId,
    currentUser,
    loading,
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
            title={localTitle}
            content={localContent}
            onChange={handleChange}
          />

          <aside className="w-full lg:w-80 lg:min-w-80 border-t lg:border-t-0 lg:border-l border-base-300 bg-base-100 px-3 sm:px-4 py-3 sm:py-4 flex flex-col gap-3 max-h-[45vh] lg:max-h-none overflow-y-auto">
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
            />
          </aside>
        </div>
      )}
    </div>
  );
}

export default NoteEditor;
