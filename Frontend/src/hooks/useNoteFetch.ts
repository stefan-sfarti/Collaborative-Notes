import type { Dispatch, MutableRefObject, SetStateAction } from "react";
import { useEffect, useRef, useState } from "react";
import NoteService from "../services/NoteService";
import type { Note, User } from "../types";

interface UseNoteFetchParams {
  noteId: string | undefined;
  currentUser: User | null;
  lookupUserById: (userId: string) => Promise<void>;
}

interface UseNoteFetchReturn {
  note: Note;
  localTitle: string;
  localContent: string;
  collaborators: string[];
  isOwner: boolean;
  owner: string | null;
  loading: boolean;
  error: string;
  setNote: Dispatch<SetStateAction<Note>>;
  setLocalTitle: Dispatch<SetStateAction<string>>;
  setLocalContent: Dispatch<SetStateAction<string>>;
  setCollaborators: Dispatch<SetStateAction<string[]>>;
  setError: Dispatch<SetStateAction<string>>;
  lastSavedContentRef: MutableRefObject<{ title: string; content: string }>;
}

const EMPTY_NOTE: Note = {
  id: "",
  title: "",
  content: "",
  ownerId: "",
  collaboratorIds: [],
  createdAt: "",
  updatedAt: "",
};

export function useNoteFetch({ noteId, currentUser, lookupUserById }: UseNoteFetchParams): UseNoteFetchReturn {
  const [note, setNote] = useState<Note>(EMPTY_NOTE);
  const [localTitle, setLocalTitle] = useState("");
  const [localContent, setLocalContent] = useState("");
  const [collaborators, setCollaborators] = useState<string[]>([]);
  const [isOwner, setIsOwner] = useState(false);
  const [owner, setOwner] = useState<string | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const lastSavedContentRef = useRef({ title: "", content: "" });

  useEffect(() => {
    const fetchNote = async () => {
      try {
        const noteData = await NoteService.getNoteById(noteId as string);
        setNote(noteData);
        setLocalTitle(noteData.title || "");
        setLocalContent(noteData.content || "");
        lastSavedContentRef.current = {
          title: noteData.title || "",
          content: noteData.content || "",
        };
        setCollaborators(Array.from(new Set(noteData.collaboratorIds || [])));
        setIsOwner(noteData.ownerId === currentUser?.id);
        setOwner(noteData.ownerId);

        if (noteData.ownerId) lookupUserById(noteData.ownerId);
        if (noteData.collaboratorIds) {
          noteData.collaboratorIds.forEach((id) => lookupUserById(id));
        }
      } catch (err) {
        setError((err as Error).message || "Failed to load note");
      } finally {
        setLoading(false);
      }
    };

    if (noteId && currentUser) {
      fetchNote();
    }
  }, [noteId, currentUser, lookupUserById]);

  return {
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
  };
}
