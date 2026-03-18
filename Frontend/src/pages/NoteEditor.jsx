// src/pages/NoteEditor.jsx
import React, { useState, useEffect, useRef } from "react";
import { useParams } from "react-router-dom";
import { useAuth } from "../contexts/AuthContext";
import NoteService from "../services/NoteService";
import ActiveUsersList from "../components/ActiveUsersList";
import CollaboratorsList from "../components/CollaboratorsList";
import EditorHeader from "../components/EditorHeader";
import RichTextEditor from "../components/RichTextEditor";
import { useWebSocket } from "../services/WebSocketProvider.jsx";
import { useActiveUsers } from "../hooks/useActiveUsers";

function NoteEditor() {
  const { noteId } = useParams();
  const { currentUser } = useAuth();

  const [note, setNote] = useState({ title: "", content: "" });
  const [localContent, setLocalContent] = useState("");
  const [localTitle, setLocalTitle] = useState("");
  const [collaborators, setCollaborators] = useState([]);

  const {
    activeUsersState,
    setActiveUsersState,
    fetchedUserDetails,
    lookupUserById,
    activeUsersForList,
  } = useActiveUsers();

  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [notification, setNotification] = useState("");
  const [showNotification, setShowNotification] = useState(false);
  const [isOwner, setIsOwner] = useState(false);
  const [owner, setOwner] = useState(null);
  const [isNoteModified, setIsNoteModified] = useState(false);

  const {
    connected,
    subscribeToNote,
    unsubscribeFromNote,
    sendNoteUpdate,
    sendTypingIndicator,
  } = useWebSocket();

  const saveTimeoutRef = useRef(null);
  const typingTimeoutRef = useRef(null);
  const fetchedUserDetailsRef = useRef(fetchedUserDetails);
  const lastSavedContentRef = useRef({ title: "", content: "" });
  const pendingUpdatesRef = useRef(0);

  useEffect(() => {
    fetchedUserDetailsRef.current = fetchedUserDetails;
  }, [fetchedUserDetails]);

  // Initial note load
  useEffect(() => {
    const fetchNote = async () => {
      try {
        const noteData = await NoteService.getNoteById(noteId);

        setNote(noteData);
        setLocalTitle(noteData.title || "");
        setLocalContent(noteData.content || "");
        lastSavedContentRef.current = {
          title: noteData.title || "",
          content: noteData.content || "",
        };
        setCollaborators(noteData.collaboratorIds || []);
        setIsOwner(noteData.ownerId === currentUser?.id);
        setOwner(noteData.ownerId);
        setLoading(false);

        // Initial lookup for owner and collaborators
        if (noteData.ownerId) lookupUserById(noteData.ownerId);
        if (noteData.collaboratorIds) {
          noteData.collaboratorIds.forEach((id) => lookupUserById(id));
        }
      } catch (error) {
        console.error("Error fetching note:", error);
        setError(
          "Failed to load note: " +
            (error.response?.data?.message || error.message),
        );
        setLoading(false);
      }
    };

    if (noteId && currentUser) {
      fetchNote();
    }
  }, [noteId, currentUser, lookupUserById]);

  // Subscribe/unsubscribe note channel exactly once per active note connection.
  useEffect(() => {
    if (!connected || !noteId || !currentUser?.id) return;

    subscribeToNote(noteId);

    return () => {
      unsubscribeFromNote(noteId);
    };
  }, [
    connected,
    noteId,
    currentUser?.id,
    subscribeToNote,
    unsubscribeFromNote,
  ]);

  // WebSocket event handling
  useEffect(() => {
    if (loading || !currentUser?.id || !noteId) return;

    console.log("Setting up WebSocket connection and event handlers");

    const handleNoteUpdate = (e) => {
      const data = e.detail;
      console.log("Received note update:", data);

      if (data.userId !== currentUser.id) {
        setNote((prev) => {
          const updatedNote = {
            ...prev,
            title: data.title,
            content: data.content,
          };
          setLocalTitle(data.title || "");
          setLocalContent(data.content || "");
          lastSavedContentRef.current = {
            title: data.title || "",
            content: data.content || "",
          };
          return updatedNote;
        });

        setNotification("Document updated by another user");
        setShowNotification(true);
      }
    };

    // Handler for initial note state and subsequent full state updates
    const handleNoteState = (e) => {
      console.log(
        "NOTE_EDITOR: Received 'note-state' event. Detail:",
        e.detail,
      );

      const data = e.detail;
      if (!data) {
        console.error("NOTE_EDITOR: Note state data is empty or undefined");
        return;
      }

      console.log("NOTE_EDITOR: Processing note state with title:", data.title);

      // Update note content and title
      setNote((prev) => ({
        ...prev,
        title: data.title || prev.title,
        content: data.content || prev.content,
      }));

      // Update local state
      if (data.title !== undefined) setLocalTitle(data.title || "");
      if (data.content !== undefined) setLocalContent(data.content || "");

      // Update last saved content reference
      if (data.title !== undefined || data.content !== undefined) {
        lastSavedContentRef.current = {
          title: data.title || "",
          content: data.content || "",
        };
      }

      // Process active users from the state message
      if (data.activeUsers) {
        console.log(
          "NOTE_EDITOR: Processing active users from note-state:",
          data.activeUsers,
        );
        try {
          const incomingActiveUsers = data.activeUsers;
          setActiveUsersState((prevActiveUsers) => {
            const updatedUsers = new Map(prevActiveUsers);

            if (
              typeof incomingActiveUsers === "object" &&
              incomingActiveUsers !== null
            ) {
              Object.entries(incomingActiveUsers).forEach(
                ([userId, userData]) => {
                  if (userId) lookupUserById(userId);

                  updatedUsers.set(userId, {
                    userId: userId,
                    isTyping: prevActiveUsers.has(userId)
                      ? prevActiveUsers.get(userId).isTyping
                      : false,
                  });
                },
              );
            } else if (Array.isArray(incomingActiveUsers)) {
              incomingActiveUsers.forEach((user) => {
                const userId = user.userId || user.id;
                if (userId) {
                  lookupUserById(userId);
                  updatedUsers.set(userId, {
                    userId: userId,
                    isTyping: prevActiveUsers.has(userId)
                      ? prevActiveUsers.get(userId).isTyping
                      : false,
                  });
                }
              });
            } else {
              console.warn(
                "NOTE_EDITOR: data.activeUsers from note-state is not a processable object or array:",
                incomingActiveUsers,
              );
            }

            // Remove users who are no longer in the active list
            const incomingUserIds = new Set(
              Array.isArray(incomingActiveUsers)
                ? incomingActiveUsers
                    .map((user) => user.userId || user.id)
                    .filter(Boolean)
                : typeof incomingActiveUsers === "object" &&
                    incomingActiveUsers !== null
                  ? Object.keys(incomingActiveUsers)
                  : [],
            );

            prevActiveUsers.forEach((_, userId) => {
              if (!incomingUserIds.has(userId)) {
                updatedUsers.delete(userId);
              }
            });

            console.log(
              "NOTE_EDITOR: Updated active users state:",
              updatedUsers,
            );
            return updatedUsers;
          });
        } catch (error) {
          console.error(
            "NOTE_EDITOR: Error processing active users from state:",
            error,
          );
        }
      }

      // Process collaborators from the state message - store IDs and trigger lookup
      if (data.collaborators) {
        console.log(
          "NOTE_EDITOR: Processing collaborators from note-state:",
          data.collaborators,
        );
        try {
          const collabArray =
            typeof data.collaborators === "object" &&
            data.collaborators !== null
              ? Object.values(data.collaborators)
              : Array.isArray(data.collaborators)
                ? data.collaborators
                : [];

          const collaboratorIds = collabArray
            .map((c) => {
              const userId = c.userId || c.id;
              if (userId) lookupUserById(userId);
              return userId;
            })
            .filter(Boolean);

          console.log("NOTE_EDITOR: Setting collaborators:", collaboratorIds);
          setCollaborators(collaboratorIds);
        } catch (error) {
          console.error(
            "NOTE_EDITOR: Error processing collaborators from state:",
            error,
          );
        }
      }
    };

    // Handler for individual user presence updates (joining/leaving)
    const handleUserPresence = (e) => {
      const data = e.detail;
      console.log("Received user presence update:", data);

      const presenceUserId = data?.userId || data?.id;
      const isJoining =
        typeof data?.joining === "boolean" ? data.joining : data?.isJoining;

      if (!presenceUserId || typeof isJoining !== "boolean") {
        console.warn("Ignoring malformed presence payload:", data);
        return;
      }

      lookupUserById(presenceUserId);

      setActiveUsersState((prevActiveUsers) => {
        const updatedUsers = new Map(prevActiveUsers);

        if (isJoining) {
          updatedUsers.set(presenceUserId, {
            userId: presenceUserId,
            isTyping: prevActiveUsers.has(presenceUserId)
              ? prevActiveUsers.get(presenceUserId).isTyping
              : false,
          });
        } else {
          updatedUsers.delete(presenceUserId);
        }
        console.log(
          "NOTE_EDITOR: Updated active users state from presence:",
          updatedUsers,
        );
        return updatedUsers;
      });

      const userDetails = fetchedUserDetailsRef.current[presenceUserId];
      const userDisplay =
        userDetails?.email ||
        userDetails?.displayName ||
        data.userName ||
        "A user";

      if (isJoining) {
        setNotification(`${userDisplay} joined the document`);
      } else {
        setNotification(`${userDisplay} left the document`);
      }
      setShowNotification(true);
    };

    const handleTypingIndicator = (e) => {
      const data = e.detail;
      console.log("Received typing indicator:", data);

      setActiveUsersState((prevActiveUsers) => {
        const updatedUsers = new Map(prevActiveUsers);
        const user = updatedUsers.get(data.userId);

        if (user) {
          updatedUsers.set(data.userId, { ...user, isTyping: data.isTyping });
          console.log(
            `NOTE_EDITOR: Updated typing status for ${data.userId} to ${data.isTyping}`,
          );
        } else {
          console.warn(
            `NOTE_EDITOR: Typing indicator received for unknown user ${data.userId}. Adding with typing status.`,
          );
          updatedUsers.set(data.userId, {
            userId: data.userId,
            isTyping: data.isTyping,
          });
          lookupUserById(data.userId);
        }
        console.log(
          "NOTE_EDITOR: Updated active users state from typing:",
          updatedUsers,
        );
        return updatedUsers;
      });
    };

    const handleError = (e) => {
      const errorData = e.detail;
      console.error("Received WebSocket error:", errorData);
      setError(errorData.message || "An error occurred");
    };

    // Event Listener Subscriptions
    window.addEventListener("note-update", handleNoteUpdate);
    window.addEventListener("note-state", handleNoteState);
    window.addEventListener("user-presence", handleUserPresence);
    window.addEventListener("typing-indicator", handleTypingIndicator);
    window.addEventListener("websocket-error", handleError);

    // Cleanup Function
    return () => {
      console.log("Cleaning up WebSocket event listeners and subscriptions");

      window.removeEventListener("note-update", handleNoteUpdate);
      window.removeEventListener("note-state", handleNoteState);
      window.removeEventListener("user-presence", handleUserPresence);
      window.removeEventListener("typing-indicator", handleTypingIndicator);
      window.removeEventListener("websocket-error", handleError);

      if (saveTimeoutRef.current) {
        clearTimeout(saveTimeoutRef.current);
      }
      if (typingTimeoutRef.current) {
        clearTimeout(typingTimeoutRef.current);
      }
    };
  }, [noteId, currentUser, loading, lookupUserById, setActiveUsersState]);

  // Save note function
  const saveNote = async () => {
    if (saving) return;

    if (
      localTitle === lastSavedContentRef.current.title &&
      localContent === lastSavedContentRef.current.content
    ) {
      setIsNoteModified(false);
      return;
    }

    try {
      setSaving(true);
      pendingUpdatesRef.current += 1;

      const titleToSave = localTitle;
      const contentToSave = localContent;

      const updatedNote = {
        ...note,
        title: titleToSave,
        content: contentToSave,
      };
      setNote(updatedNote);

      console.log("Saving note to server via HTTP:", updatedNote);
      await NoteService.updateNote(noteId, updatedNote);

      console.log(
        "Sending note update through WebSocket:",
        titleToSave,
        contentToSave,
      );
      sendNoteUpdate(noteId, titleToSave, contentToSave);

      lastSavedContentRef.current = {
        title: titleToSave,
        content: contentToSave,
      };

      pendingUpdatesRef.current -= 1;
      setSaving(false);
      setIsNoteModified(false);

      console.log("Note saved successfully");
    } catch (error) {
      console.error("Error saving note:", error);
      setError(
        "Failed to save: " + (error.response?.data?.message || error.message),
      );
      pendingUpdatesRef.current -= 1;
      setSaving(false);
    }
  };

  // Force save on demand
  const handleForceSave = () => {
    if (saveTimeoutRef.current) {
      clearTimeout(saveTimeoutRef.current);
      saveTimeoutRef.current = null;
    }
    saveNote();
  };

  const handleChange = (field, value) => {
    if (field === "title") {
      setLocalTitle(value);
    } else if (field === "content") {
      setLocalContent(value);
    }

    setIsNoteModified(true);

    if (typingTimeoutRef.current) {
      clearTimeout(typingTimeoutRef.current);
    } else {
      if (pendingUpdatesRef.current === 0) {
        sendTypingIndicator(noteId, true);
      }
    }

    typingTimeoutRef.current = setTimeout(() => {
      sendTypingIndicator(noteId, false);
      typingTimeoutRef.current = null;
    }, 2000);

    if (saveTimeoutRef.current) {
      clearTimeout(saveTimeoutRef.current);
    }

    saveTimeoutRef.current = setTimeout(() => {
      saveNote();
      saveTimeoutRef.current = null;
    }, 1500);
  };

  const handleCollaboratorChange = (updatedCollaborators) => {
    setCollaborators(updatedCollaborators);
  };

  const handleCloseNotification = () => {
    setShowNotification(false);
  };

  return (
    <div className="flex flex-col min-h-[100dvh] bg-base-200">
      <EditorHeader
        isOwner={isOwner}
        saving={saving}
        isNoteModified={isNoteModified}
        connected={connected}
        onForceSave={handleForceSave}
      />

      {error && (
        <div className="alert alert-error m-3" onClick={() => setError("")}>
          <span>{error}</span>
        </div>
      )}

      {showNotification && (
        <div className="toast toast-center toast-bottom z-50">
          <div className="alert alert-info">
            <span>{notification}</span>
            <button
              type="button"
              className="btn btn-ghost btn-xs ml-2"
              onClick={handleCloseNotification}
            >
              Close
            </button>
          </div>
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
              onCollaboratorChange={handleCollaboratorChange}
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
