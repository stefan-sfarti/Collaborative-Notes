package com.collabnotes.collabnotes.service;

import java.util.List;

import com.collabnotes.collabnotes.dto.NoteDTO;

public interface NoteService {
    NoteDTO createNote(NoteDTO noteDTO, String userId);

    NoteDTO getNoteById(String id, String userId);

    List<NoteDTO> getAllNotesByUser(String userId);

    NoteDTO updateNote(String id, NoteDTO noteDTO, String userId);

    boolean deleteNote(String id, String userId);

    boolean addCollaborator(String noteId, String collaboratorId, String userId);

    boolean inviteCollaboratorByEmail(String noteId, String email, String userId);

    boolean removeCollaborator(String noteId, String collaboratorId, String userId);

    List<String> getNoteCollaborators(String noteId, String userId);

    boolean hasNoteAccess(String noteId, String userId);
}
