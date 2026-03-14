package com.collabnotes.collabnotes.service;

import java.util.List;

import org.springframework.lang.NonNull;

import com.collabnotes.collabnotes.dto.NoteDTO;

public interface NoteService {
    NoteDTO createNote(NoteDTO noteDTO, String userId);

    NoteDTO getNoteById(@NonNull String id, String userId);

    List<NoteDTO> getAllNotesByUser(String userId);

    NoteDTO updateNote(String id, NoteDTO noteDTO, String userId);

    boolean deleteNote(String id, String userId);

    boolean addCollaborator(String noteId, String collaboratorId, String userId);

    boolean removeCollaborator(String noteId, String collaboratorId, String userId);

    List<String> getNoteCollaborators(String noteId, String userId);
}
