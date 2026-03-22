import { useState, useRef, useCallback, useEffect } from "react";
import type { Dispatch, SetStateAction, MutableRefObject } from "react";
import NoteService from "../services/NoteService";
import { createApiError } from "../utils/errorUtils";
import type { Note } from "../types";
// sendNoteUpdate removed: real-time broadcast is now handled by OT steps (useOTCollab).

interface UseSaveManagerParams {
  noteId: string | undefined;
  note: Note;
  localTitle: string;
  localContent: string;
  lastSavedContentRef: MutableRefObject<{ title: string; content: string }>;
  setLocalTitle: Dispatch<SetStateAction<string>>;
  setLocalContent: Dispatch<SetStateAction<string>>;
  setNote: Dispatch<SetStateAction<Note>>;
  sendTypingIndicator: (noteId: string, isTyping: boolean) => Promise<void>;
}

interface UseSaveManagerReturn {
  saving: boolean;
  saveError: string | null;
  isNoteModified: boolean;
  handleForceSave: () => void;
  handleChange: (field: "title" | "content", value: string) => void;
}

export function useSaveManager({
  noteId,
  note,
  localTitle,
  localContent,
  lastSavedContentRef,
  setLocalTitle,
  setLocalContent,
  setNote,
  sendTypingIndicator,
}: UseSaveManagerParams): UseSaveManagerReturn {
  const [saving, setSaving] = useState(false);
  const [saveError, setSaveError] = useState<string | null>(null);
  const [isNoteModified, setIsNoteModified] = useState(false);

  // Synchronous guard — prevents double-saves from stale closure state
  const savingRef = useRef(false);
  const saveTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  const typingTimeoutRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // Keep refs to latest values so timeouts always read current state
  const latestTitleRef = useRef(localTitle);
  const latestContentRef = useRef(localContent);
  const latestNoteRef = useRef(note);

  useEffect(() => { latestTitleRef.current = localTitle; }, [localTitle]);
  useEffect(() => { latestContentRef.current = localContent; }, [localContent]);
  useEffect(() => { latestNoteRef.current = note; }, [note]);

  const saveNote = useCallback(async () => {
    if (savingRef.current) return;

    const titleToSave = latestTitleRef.current;
    const contentToSave = latestContentRef.current;

    if (
      titleToSave === lastSavedContentRef.current.title &&
      contentToSave === lastSavedContentRef.current.content
    ) {
      setIsNoteModified(false);
      return;
    }

    savingRef.current = true;
    setSaving(true);

    try {
      // Only send title + content. Omit `version` so the backend skips
      // optimistic lock checking — OT handles real-time concurrency, the
      // REST save is just periodic persistence (last-writer-wins is safe).
      const updatedNote = {
        title: titleToSave,
        content: contentToSave,
      };
      const savedNote = await NoteService.updateNote(noteId as string, updatedNote);
      lastSavedContentRef.current = { title: titleToSave, content: contentToSave };
      // Sync the note version from the server response so subsequent saves
      // don't use a stale version and trigger a 409 conflict.
      setNote(savedNote);
      setIsNoteModified(false);
      setSaveError(null);
    } catch (err) {
      const apiError = createApiError(err);
      setSaveError(apiError.message);
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  }, [noteId, lastSavedContentRef, setNote]);

  const handleForceSave = useCallback(() => {
    if (saveTimeoutRef.current) {
      clearTimeout(saveTimeoutRef.current);
      saveTimeoutRef.current = null;
    }
    setSaveError(null);
    saveNote();
  }, [saveNote]);

  const handleChange = useCallback(
    (field: "title" | "content", value: string) => {
      if (field === "title") setLocalTitle(value);
      else if (field === "content") setLocalContent(value);

      setIsNoteModified(true);

      if (typingTimeoutRef.current) {
        clearTimeout(typingTimeoutRef.current);
      } else {
        if (!savingRef.current) {
          sendTypingIndicator(noteId as string, true);
        }
      }

      typingTimeoutRef.current = setTimeout(() => {
        sendTypingIndicator(noteId as string, false);
        typingTimeoutRef.current = null;
      }, 2000);

      if (saveTimeoutRef.current) {
        clearTimeout(saveTimeoutRef.current);
      }

      saveTimeoutRef.current = setTimeout(() => {
        saveNote();
        saveTimeoutRef.current = null;
      }, 1500);
    },
    [noteId, setLocalTitle, setLocalContent, sendTypingIndicator, saveNote],
  );

  return { saving, saveError, isNoteModified, handleForceSave, handleChange };
}
