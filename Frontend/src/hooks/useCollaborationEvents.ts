import type { Dispatch, MutableRefObject, SetStateAction } from "react";
import { useEffect, useRef } from "react";
import toast from "react-hot-toast";
import type {
  ActiveUserEntry,
  ActiveUserPayload,
  CollaboratorPayload,
  Note,
  NoteStateEventDetail,
  TypingIndicatorEventDetail,
  User,
  UserPresenceEventDetail,
  WebSocketErrorEventDetail,
} from "../types";

interface UseCollaborationEventsParams {
  noteId: string | undefined;
  currentUser: User | null;
  isOTActive: boolean;
  setNote: Dispatch<SetStateAction<Note>>;
  setLocalTitle: Dispatch<SetStateAction<string>>;
  setLocalContent: Dispatch<SetStateAction<string>>;
  lastSavedContentRef: MutableRefObject<{ title: string; content: string }>;
  setActiveUsersState: Dispatch<SetStateAction<Map<string, ActiveUserEntry>>>;
  setCollaborators: Dispatch<SetStateAction<string[]>>;
  lookupUserById: (userId: string) => Promise<void>;
}

export function useCollaborationEvents({
  noteId,
  currentUser,
  isOTActive,
  setNote,
  setLocalTitle,
  setLocalContent,
  lastSavedContentRef,
  setActiveUsersState,
  setCollaborators,
  lookupUserById,
}: UseCollaborationEventsParams): void {
  // Keep a ref to fetchedUserDetails for use inside event handlers
  // (useActiveUsers manages the actual map; we track it via lookupUserById calls)
  const lookupUserByIdRef = useRef(lookupUserById);
  const isOTActiveRef = useRef(isOTActive);
  const recentPresenceEventsRef = useRef<Map<string, number>>(new Map());
  const suppressPresenceToastsUntilRef = useRef(0);

  useEffect(() => {
    lookupUserByIdRef.current = lookupUserById;
  }, [lookupUserById]);

  useEffect(() => {
    isOTActiveRef.current = isOTActive;
  }, [isOTActive]);

  useEffect(() => {
    // Suppress initial presence burst right after joining to avoid noisy duplicate toasts.
    suppressPresenceToastsUntilRef.current = Date.now() + 3000;
    recentPresenceEventsRef.current.clear();
  }, [noteId, currentUser?.id]);

  useEffect(() => {
    if (!currentUser?.id || !noteId) return;

    const handleNoteState = (e: CustomEvent<NoteStateEventDetail>) => {
      const data = e.detail;

      // When OT is active, the editor state is owned by prosemirror-collab.
      // Don't overwrite localContent/localTitle — that would desync the
      // editor from the save manager and cause stale REST saves.
      if (!isOTActiveRef.current) {
        setNote((prev) => ({
          ...prev,
          title: data.title || prev.title,
          content: data.content || prev.content,
        }));

        if (data.title !== undefined) setLocalTitle(data.title || "");
        if (data.content !== undefined) setLocalContent(data.content || "");

        if (data.title !== undefined || data.content !== undefined) {
          lastSavedContentRef.current = {
            title: data.title || "",
            content: data.content || "",
          };
        }
      }

      if (data.activeUsers) {
        try {
          const incomingActiveUsers = data.activeUsers;
          setActiveUsersState((prevActiveUsers) => {
            const updatedUsers = new Map(prevActiveUsers);

            if (
              typeof incomingActiveUsers === "object" &&
              incomingActiveUsers !== null &&
              !Array.isArray(incomingActiveUsers)
            ) {
              Object.entries(incomingActiveUsers).forEach(([userId]) => {
                if (userId) lookupUserByIdRef.current(userId);
                updatedUsers.set(userId, {
                  userId,
                  isTyping: prevActiveUsers.has(userId)
                    ? prevActiveUsers.get(userId)!.isTyping
                    : false,
                });
              });
            } else if (Array.isArray(incomingActiveUsers)) {
              (incomingActiveUsers as ActiveUserPayload[]).forEach((user) => {
                const userId = user.userId || user.id;
                if (userId) {
                  lookupUserByIdRef.current(userId);
                  updatedUsers.set(userId, {
                    userId,
                    isTyping: prevActiveUsers.has(userId)
                      ? prevActiveUsers.get(userId)!.isTyping
                      : false,
                  });
                }
              });
            }

            const incomingUserIds = new Set(
              Array.isArray(incomingActiveUsers)
                ? (incomingActiveUsers as ActiveUserPayload[])
                    .map((user) => user.userId || user.id)
                    .filter((id): id is string => Boolean(id))
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

            return updatedUsers;
          });
        } catch (error) {
          console.error("Error processing active users from state:", error);
        }
      }

      if (data.collaborators) {
        try {
          const collabRaw = data.collaborators;
          const collabArray: CollaboratorPayload[] = Array.isArray(collabRaw)
            ? (collabRaw as CollaboratorPayload[])
            : typeof collabRaw === "object" && collabRaw !== null
              ? Object.values(collabRaw) as CollaboratorPayload[]
              : [];

          const collaboratorIds = collabArray
            .map((c) => {
              const userId = c.userId || c.id;
              if (userId) lookupUserByIdRef.current(userId);
              return userId;
            })
            .filter((id): id is string => Boolean(id));

          setCollaborators(Array.from(new Set(collaboratorIds)));
        } catch (error) {
          console.error("Error processing collaborators from state:", error);
        }
      }
    };

    const handleUserPresence = (e: CustomEvent<UserPresenceEventDetail>) => {
      const data = e.detail;
      const presenceUserId = data.userId || data.id;
      const isJoining =
        typeof data.joining === "boolean" ? data.joining : data.isJoining;

      if (!presenceUserId || typeof isJoining !== "boolean") {
        console.warn("Ignoring malformed presence payload:", data);
        return;
      }

      const eventType = isJoining ? "join" : "leave";
      const eventKey = `${presenceUserId}:${eventType}`;
      const now = Date.now();
      const lastSeen = recentPresenceEventsRef.current.get(eventKey);
      if (lastSeen && now - lastSeen < 3000) {
        return;
      }
      recentPresenceEventsRef.current.set(eventKey, now);

      // Keep the cache bounded over long sessions.
      if (recentPresenceEventsRef.current.size > 100) {
        const cutoff = now - 10000;
        recentPresenceEventsRef.current.forEach((timestamp, key) => {
          if (timestamp < cutoff) {
            recentPresenceEventsRef.current.delete(key);
          }
        });
      }

      lookupUserByIdRef.current(presenceUserId);

      setActiveUsersState((prevActiveUsers) => {
        const updatedUsers = new Map(prevActiveUsers);
        if (isJoining) {
          updatedUsers.set(presenceUserId, {
            userId: presenceUserId,
            isTyping: prevActiveUsers.has(presenceUserId)
              ? prevActiveUsers.get(presenceUserId)!.isTyping
              : false,
          });
        } else {
          updatedUsers.delete(presenceUserId);
        }
        return updatedUsers;
      });

      const isCurrentUser = presenceUserId === currentUser.id;
      const shouldSuppressInitialToast =
        now < suppressPresenceToastsUntilRef.current;

      if (!isCurrentUser && !shouldSuppressInitialToast) {
        const userDisplay =
          data.userName?.trim() ||
          `User ${String(presenceUserId).substring(0, 6)}`;
        if (isJoining) {
          toast(`${userDisplay} joined the document`);
        } else {
          toast(`${userDisplay} left the document`);
        }
      }
    };

    const handleTypingIndicator = (e: CustomEvent<TypingIndicatorEventDetail>) => {
      const data = e.detail;
      setActiveUsersState((prevActiveUsers) => {
        const updatedUsers = new Map(prevActiveUsers);
        const user = updatedUsers.get(data.userId);
        if (user) {
          updatedUsers.set(data.userId, { ...user, isTyping: data.isTyping });
        } else {
          updatedUsers.set(data.userId, {
            userId: data.userId,
            isTyping: data.isTyping,
          });
          lookupUserByIdRef.current(data.userId);
        }
        return updatedUsers;
      });
    };

    const handleError = (e: CustomEvent<WebSocketErrorEventDetail>) => {
      const errorData = e.detail;
      console.error("Received WebSocket error:", errorData);
      toast.error(errorData.message || "An error occurred");
    };

    window.addEventListener("note-state", handleNoteState);
    window.addEventListener("user-presence", handleUserPresence);
    window.addEventListener("typing-indicator", handleTypingIndicator);
    window.addEventListener("websocket-error", handleError);

    return () => {
      window.removeEventListener("note-state", handleNoteState);
      window.removeEventListener("user-presence", handleUserPresence);
      window.removeEventListener("typing-indicator", handleTypingIndicator);
      window.removeEventListener("websocket-error", handleError);
    };
  }, [
    noteId,
    currentUser,
    setNote,
    setLocalTitle,
    setLocalContent,
    lastSavedContentRef,
    setActiveUsersState,
    setCollaborators,
  ]);
}
