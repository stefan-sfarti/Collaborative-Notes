package com.collabnotes.CollabNotes.service;

import com.collabnotes.CollabNotes.dto.NoteDTO;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;

public interface NoteService {
    NoteDTO createNote(NoteDTO noteDTO, String userId) throws ExecutionException, InterruptedException;
    NoteDTO getNoteById(String id, String userId) throws ExecutionException, InterruptedException;
    List<NoteDTO> getAllNotesByUser(String userId) throws ExecutionException, InterruptedException;
    NoteDTO updateNote(String id, NoteDTO noteDTO, String userId) throws ExecutionException, InterruptedException;
    boolean deleteNote(String id, String userId) throws ExecutionException, InterruptedException;
    boolean addCollaborator(String noteId, String collaboratorId, String userId) throws ExecutionException, InterruptedException;
    boolean removeCollaborator(String noteId, String collaboratorId, String userId) throws ExecutionException, InterruptedException;
    List<String> getNoteCollaborators(String noteId, String userId) throws ExecutionException, InterruptedException;
}