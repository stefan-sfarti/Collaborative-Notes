package com.collabnotes.collabnotes.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.CacheManager;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.collabnotes.collabnotes.dto.NoteDTO;
import com.collabnotes.collabnotes.entity.Collaborator;
import com.collabnotes.collabnotes.entity.Note;
import com.collabnotes.collabnotes.entity.User;
import com.collabnotes.collabnotes.repository.CollaboratorRepository;
import com.collabnotes.collabnotes.repository.NoteRepository;
import com.collabnotes.collabnotes.repository.UserRepository;

@ExtendWith(MockitoExtension.class)
class NoteServiceImplTest {

    @Mock
    private NoteRepository noteRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private CollaboratorRepository collaboratorRepository;

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private NoteEventPublisher noteEventPublisher;

    @Mock
    private CacheManager cacheManager;

    @Mock
    private NoteServiceImpl selfProxy;

    private NoteServiceImpl noteService;

    @BeforeEach
    void setUp() {
        noteService = new NoteServiceImpl(
                noteRepository,
                userRepository,
                collaboratorRepository,
                messagingTemplate,
                noteEventPublisher,
            cacheManager,
                selfProxy);
    }

    @Test
    void createNote_savesAndPublishesEvent() {
        NoteDTO request = new NoteDTO();
        request.setTitle("Title");
        request.setContent("Content");

        when(noteRepository.save(any(Note.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(collaboratorRepository.findByNoteId(any(String.class))).thenReturn(List.of());

        NoteDTO created = noteService.createNote(request, "owner-1");

        assertNotNull(created.getId());
        assertEquals("Title", created.getTitle());
        assertEquals("Content", created.getContent());
        assertEquals("owner-1", created.getOwnerId());
        verify(noteEventPublisher).publishNoteUpdate(created.getId(), "owner-1", "create");
    }

    @Test
    void getNoteById_whenNoAccess_returnsNull() {
        when(selfProxy.hasNoteAccess("note-1", "user-1")).thenReturn(false);

        NoteDTO result = noteService.getNoteById("note-1", "user-1");

        assertNull(result);
        verify(noteRepository, never()).findById(any(String.class));
    }

    @Test
    void getNoteById_whenAccessGranted_returnsMappedDto() {
        Note note = createNote("note-1", "owner-1");
        Collaborator collaborator = new Collaborator(note, createUser("collab-1", "c@example.com"));

        when(selfProxy.hasNoteAccess("note-1", "user-1")).thenReturn(true);
        when(noteRepository.findById("note-1")).thenReturn(Optional.of(note));
        when(collaboratorRepository.findByNoteId("note-1")).thenReturn(List.of(collaborator));

        NoteDTO result = noteService.getNoteById("note-1", "user-1");

        assertNotNull(result);
        assertEquals("note-1", result.getId());
        assertEquals(List.of("collab-1"), result.getCollaboratorIds());
    }

    @Test
    void getAllNotesByUser_mergesOwnerAndCollaboratorNotes() {
        Note ownerNote = createNote("note-owner", "user-1");
        Note sharedNote = createNote("note-shared", "other");

        when(noteRepository.findByOwnerId("user-1")).thenReturn(List.of(ownerNote));
        when(noteRepository.findByCollaboratorUserId("user-1")).thenReturn(List.of(sharedNote));
        when(collaboratorRepository.findByNoteId("note-owner")).thenReturn(List.of());
        when(collaboratorRepository.findByNoteId("note-shared")).thenReturn(List.of());

        List<NoteDTO> results = noteService.getAllNotesByUser("user-1");

        assertEquals(2, results.size());
    }

    @Test
    void updateNote_whenUnauthorized_returnsNull() {
        Note existing = createNote("note-1", "owner-1");
        NoteDTO request = new NoteDTO();
        request.setTitle("New");
        request.setContent("Text");

        when(noteRepository.findById("note-1")).thenReturn(Optional.of(existing));
        when(collaboratorRepository.existsByNoteIdAndUserId("note-1", "intruder")).thenReturn(false);

        NoteDTO result = noteService.updateNote("note-1", request, "intruder");

        assertNull(result);
        verify(noteRepository, never()).save(any(Note.class));
    }

    @Test
    void updateNote_whenOwner_updatesAndPublishesEvent() {
        Note existing = createNote("note-1", "owner-1");
        NoteDTO request = new NoteDTO();
        request.setTitle("Updated Title");
        request.setContent("Updated Content");
        request.setAnalysis(Map.of("status", "ok"));

        when(noteRepository.findById("note-1")).thenReturn(Optional.of(existing));
        when(noteRepository.save(any(Note.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(collaboratorRepository.findByNoteId("note-1")).thenReturn(List.of());

        NoteDTO result = noteService.updateNote("note-1", request, "owner-1");

        assertNotNull(result);
        assertEquals("Updated Title", result.getTitle());
        assertEquals("Updated Content", result.getContent());
        assertEquals("{status=ok}", existing.getAnalysis());
        verify(noteEventPublisher).publishNoteUpdate("note-1", "owner-1", "update");
    }

    @Test
    void deleteNote_whenOwner_deletesNote() {
        Note existing = createNote("note-1", "owner-1");
        when(noteRepository.findById("note-1")).thenReturn(Optional.of(existing));

        boolean deleted = noteService.deleteNote("note-1", "owner-1");

        assertTrue(deleted);
        verify(noteRepository).delete(existing);
    }

    @Test
    void addCollaborator_whenValid_addsCollaboratorAndPublishesEvents() {
        Note note = createNote("note-1", "owner-1");
        User collaboratorUser = createUser("user-2", "u2@example.com");

        when(noteRepository.findById("note-1")).thenReturn(Optional.of(note));
        when(collaboratorRepository.existsByNoteIdAndUserId("note-1", "user-2")).thenReturn(false);
        when(userRepository.findById("user-2")).thenReturn(Optional.of(collaboratorUser));

        boolean added = noteService.addCollaborator("note-1", "user-2", "owner-1");

        assertTrue(added);
        verify(collaboratorRepository).save(any(Collaborator.class));
        verify(noteRepository).save(note);
        verify(messagingTemplate).convertAndSend(eq("/topic/notes/note-1/events"), (Object) any(Map.class));
        verify(noteEventPublisher).publishNoteUpdate("note-1", "owner-1", "collaborator_added");
    }

    @Test
    void removeCollaborator_whenExists_removesAndPublishesEvents() {
        Note note = createNote("note-1", "owner-1");
        when(noteRepository.findById("note-1")).thenReturn(Optional.of(note));
        when(collaboratorRepository.existsByNoteIdAndUserId("note-1", "user-2")).thenReturn(true);

        boolean removed = noteService.removeCollaborator("note-1", "user-2", "owner-1");

        assertTrue(removed);
        verify(collaboratorRepository).deleteByNoteIdAndUserId("note-1", "user-2");
        verify(noteEventPublisher).publishNoteUpdate("note-1", "owner-1", "collaborator_removed");
    }

    @Test
    void getNoteCollaborators_whenUnauthorized_returnsEmptyList() {
        Note note = createNote("note-1", "owner-1");
        when(noteRepository.findById("note-1")).thenReturn(Optional.of(note));
        when(collaboratorRepository.existsByNoteIdAndUserId("note-1", "intruder")).thenReturn(false);

        List<String> collaborators = noteService.getNoteCollaborators("note-1", "intruder");

        assertTrue(collaborators.isEmpty());
    }

    @Test
    void hasNoteAccess_whenOwnerOrCollaborator_returnsTrue() {
        Note note = createNote("note-1", "owner-1");
        when(noteRepository.findById("note-1")).thenReturn(Optional.of(note));
        when(collaboratorRepository.existsByNoteIdAndUserId("note-1", "collab-1")).thenReturn(true);

        assertTrue(noteService.hasNoteAccess("note-1", "owner-1"));
        assertTrue(noteService.hasNoteAccess("note-1", "collab-1"));
        assertFalse(noteService.hasNoteAccess("note-1", "other"));
    }

    @Test
    void updateNote_withNullId_returnsNull() {
        NoteDTO request = new NoteDTO();
        request.setTitle("Updated Title");

        assertNull(noteService.updateNote(null, request, "owner-1"));
    }

    @Test
    void addCollaborator_whenAlreadyCollaborator_returnsFalse() {
        Note note = createNote("note-1", "owner-1");
        when(noteRepository.findById("note-1")).thenReturn(Optional.of(note));
        when(collaboratorRepository.existsByNoteIdAndUserId("note-1", "user-2")).thenReturn(true);

        boolean added = noteService.addCollaborator("note-1", "user-2", "owner-1");

        assertFalse(added);
        verify(collaboratorRepository, never()).save(any(Collaborator.class));
    }

    private static User createUser(String id, String email) {
        User user = new User(id, email, email);
        user.setId(id);
        return user;
    }

    private static Note createNote(String id, String ownerId) {
        Note note = new Note();
        note.setId(id);
        note.setOwnerId(ownerId);
        note.setTitle("Title");
        note.setContent("Content");
        note.setCreatedAt(LocalDateTime.now().minusDays(1));
        note.setUpdatedAt(LocalDateTime.now());
        return note;
    }
}
