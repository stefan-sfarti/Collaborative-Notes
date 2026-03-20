package com.collabnotes.collabnotes.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.collabnotes.collabnotes.dto.NoteDTO;
import com.collabnotes.collabnotes.entity.Collaborator;
import com.collabnotes.collabnotes.entity.Note;
import com.collabnotes.collabnotes.entity.User;
import com.collabnotes.collabnotes.exception.ConflictException;
import com.collabnotes.collabnotes.repository.CollaboratorRepository;
import com.collabnotes.collabnotes.repository.NoteRepository;
import com.collabnotes.collabnotes.repository.UserRepository;

/**
 * Additional edge-case tests for NoteServiceImpl that complement the
 * main NoteServiceImplTest class.
 */
@ExtendWith(MockitoExtension.class)
class NoteServiceImplEdgeCaseTest {

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
    private Cache noteAccessCache;

    @Mock
    private NoteServiceImpl selfProxy;

    private NoteServiceImpl noteService;

    @BeforeEach
    void setUp() {
        noteService = new NoteServiceImpl(
                noteRepository, userRepository, collaboratorRepository,
                messagingTemplate, noteEventPublisher, cacheManager, selfProxy);
    }

    private static Note createNote(String id, String ownerId) {
        Note note = new Note();
        note.setId(id);
        note.setOwnerId(ownerId);
        note.setTitle("Title");
        note.setContent("Content");
        note.setVersion(1L);
        note.setCreatedAt(LocalDateTime.now().minusDays(1));
        note.setUpdatedAt(LocalDateTime.now());
        return note;
    }

    private static User createUser(String id, String email) {
        User user = new User(id, email, email);
        user.setId(id);
        return user;
    }

    @Nested
    class UpdateNoteVersionConflict {

        @Test
        void whenClientVersionDoesNotMatchDb_throwsConflictException() {
            Note existing = createNote("note-1", "owner-1");
            existing.setVersion(3L);

            NoteDTO request = new NoteDTO();
            request.setTitle("Updated");
            request.setContent("Text");
            request.setVersion(2L); // stale version

            when(noteRepository.findById("note-1")).thenReturn(Optional.of(existing));

            assertThrows(ConflictException.class,
                    () -> noteService.updateNote("note-1", request, "owner-1"));

            verify(noteRepository, never()).saveAndFlush(any(Note.class));
        }

        @Test
        void whenClientVersionIsNull_skipsVersionCheck() {
            Note existing = createNote("note-1", "owner-1");
            existing.setVersion(5L);

            NoteDTO request = new NoteDTO();
            request.setTitle("Updated");
            request.setContent("Text");
            request.setVersion(null);

            when(noteRepository.findById("note-1")).thenReturn(Optional.of(existing));
            when(noteRepository.saveAndFlush(any(Note.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(collaboratorRepository.findByNoteId("note-1")).thenReturn(List.of());

            NoteDTO result = noteService.updateNote("note-1", request, "owner-1");

            assertNotNull(result);
        }

        @Test
        void whenClientVersionIsZero_skipsVersionCheck() {
            Note existing = createNote("note-1", "owner-1");
            existing.setVersion(5L);

            NoteDTO request = new NoteDTO();
            request.setTitle("Updated");
            request.setContent("Text");
            request.setVersion(0L);

            when(noteRepository.findById("note-1")).thenReturn(Optional.of(existing));
            when(noteRepository.saveAndFlush(any(Note.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(collaboratorRepository.findByNoteId("note-1")).thenReturn(List.of());

            NoteDTO result = noteService.updateNote("note-1", request, "owner-1");

            assertNotNull(result);
        }

        @Test
        void whenClientVersionMatchesDb_proceeds() {
            Note existing = createNote("note-1", "owner-1");
            existing.setVersion(3L);

            NoteDTO request = new NoteDTO();
            request.setTitle("Updated");
            request.setContent("Text");
            request.setVersion(3L);

            when(noteRepository.findById("note-1")).thenReturn(Optional.of(existing));
            when(noteRepository.saveAndFlush(any(Note.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(collaboratorRepository.findByNoteId("note-1")).thenReturn(List.of());

            NoteDTO result = noteService.updateNote("note-1", request, "owner-1");

            assertNotNull(result);
            assertEquals("Updated", result.getTitle());
        }
    }

    @Nested
    class UpdateNoteAsCollaborator {

        @Test
        void whenCollaborator_canUpdate() {
            Note existing = createNote("note-1", "owner-1");

            NoteDTO request = new NoteDTO();
            request.setTitle("Collab Update");
            request.setContent("By collaborator");

            when(noteRepository.findById("note-1")).thenReturn(Optional.of(existing));
            when(collaboratorRepository.existsByNoteIdAndUserId("note-1", "collab-1"))
                    .thenReturn(true);
            when(noteRepository.saveAndFlush(any(Note.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(collaboratorRepository.findByNoteId("note-1")).thenReturn(List.of());

            NoteDTO result = noteService.updateNote("note-1", request, "collab-1");

            assertNotNull(result);
            assertEquals("Collab Update", result.getTitle());
        }
    }

    @Nested
    class UpdateNoteWithAnalysis {

        @Test
        void whenAnalysisIsNull_doesNotSetAnalysis() {
            Note existing = createNote("note-1", "owner-1");
            existing.setAnalysis("old analysis");

            NoteDTO request = new NoteDTO();
            request.setTitle("T");
            request.setContent("C");
            request.setAnalysis(null);

            when(noteRepository.findById("note-1")).thenReturn(Optional.of(existing));
            when(noteRepository.saveAndFlush(any(Note.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(collaboratorRepository.findByNoteId("note-1")).thenReturn(List.of());

            noteService.updateNote("note-1", request, "owner-1");

            // analysis should remain "old analysis" because request.analysis is null
            assertEquals("old analysis", existing.getAnalysis());
        }
    }

    @Nested
    class UpdateNoteNotFound {

        @Test
        void whenNoteDoesNotExist_returnsNull() {
            NoteDTO request = new NoteDTO();
            request.setTitle("T");

            when(noteRepository.findById("missing")).thenReturn(Optional.empty());

            assertNull(noteService.updateNote("missing", request, "user-1"));
        }
    }

    @Nested
    class DeleteNoteEdgeCases {

        @Test
        void whenNullId_returnsFalse() {
            assertFalse(noteService.deleteNote(null, "user-1"));
        }

        @Test
        void whenNoteNotFound_returnsFalse() {
            when(noteRepository.findById("missing")).thenReturn(Optional.empty());

            assertFalse(noteService.deleteNote("missing", "user-1"));
            verify(noteRepository, never()).delete(any(Note.class));
        }

        @Test
        void whenNotOwner_returnsFalse() {
            Note note = createNote("note-1", "owner-1");
            when(noteRepository.findById("note-1")).thenReturn(Optional.of(note));

            assertFalse(noteService.deleteNote("note-1", "not-owner"));
            verify(noteRepository, never()).delete(any(Note.class));
        }
    }

    @Nested
    class InviteCollaboratorByEmail {

        @Test
        void whenEmailFound_delegatesToAddCollaborator() {
            User user = createUser("user-2", "friend@example.com");
            when(userRepository.findByEmail("friend@example.com")).thenReturn(Optional.of(user));
            when(selfProxy.addCollaborator("note-1", "user-2", "owner-1")).thenReturn(true);

            boolean result = noteService.inviteCollaboratorByEmail("note-1", "friend@example.com", "owner-1");

            assertTrue(result);
            verify(selfProxy).addCollaborator("note-1", "user-2", "owner-1");
        }

        @Test
        void whenEmailNotFound_throwsIllegalArgument() {
            when(userRepository.findByEmail("nobody@example.com")).thenReturn(Optional.empty());

            assertThrows(IllegalArgumentException.class,
                    () -> noteService.inviteCollaboratorByEmail("note-1", "nobody@example.com", "owner-1"));
        }
    }

    @Nested
    class AddCollaboratorEdgeCases {

        @Test
        void whenNoteNotFound_returnsFalse() {
            when(noteRepository.findById("missing")).thenReturn(Optional.empty());

            assertFalse(noteService.addCollaborator("missing", "user-2", "owner-1"));
        }

        @Test
        void whenCallerNotOwner_returnsFalse() {
            Note note = createNote("note-1", "owner-1");
            when(noteRepository.findById("note-1")).thenReturn(Optional.of(note));

            assertFalse(noteService.addCollaborator("note-1", "user-2", "not-owner"));
            verify(collaboratorRepository, never()).save(any(Collaborator.class));
        }

        @Test
        void whenCollaboratorIsOwner_returnsFalse() {
            Note note = createNote("note-1", "owner-1");
            when(noteRepository.findById("note-1")).thenReturn(Optional.of(note));

            assertFalse(noteService.addCollaborator("note-1", "owner-1", "owner-1"));
        }

        @Test
        void whenCollaboratorUserNotFound_returnsFalse() {
            Note note = createNote("note-1", "owner-1");
            when(noteRepository.findById("note-1")).thenReturn(Optional.of(note));
            when(collaboratorRepository.existsByNoteIdAndUserId("note-1", "ghost")).thenReturn(false);
            when(userRepository.findById("ghost")).thenReturn(Optional.empty());

            assertFalse(noteService.addCollaborator("note-1", "ghost", "owner-1"));
            verify(collaboratorRepository, never()).save(any(Collaborator.class));
        }

        @Test
        void whenSuccessful_evictsCache() {
            Note note = createNote("note-1", "owner-1");
            User collabUser = createUser("user-2", "u2@e.com");

            when(noteRepository.findById("note-1")).thenReturn(Optional.of(note));
            when(collaboratorRepository.existsByNoteIdAndUserId("note-1", "user-2")).thenReturn(false);
            when(userRepository.findById("user-2")).thenReturn(Optional.of(collabUser));
            when(cacheManager.getCache("noteAccessCache")).thenReturn(noteAccessCache);

            noteService.addCollaborator("note-1", "user-2", "owner-1");

            // owner-1 appears twice in the varargs (as userId and as note.getOwnerId())
            verify(noteAccessCache, org.mockito.Mockito.atLeastOnce()).evict("note-1-owner-1");
            verify(noteAccessCache).evict("note-1-user-2");
        }
    }

    @Nested
    class RemoveCollaboratorEdgeCases {

        @Test
        void whenNoteNotFound_returnsFalse() {
            when(noteRepository.findById("missing")).thenReturn(Optional.empty());

            assertFalse(noteService.removeCollaborator("missing", "user-2", "owner-1"));
        }

        @Test
        void whenCallerNotOwner_returnsFalse() {
            Note note = createNote("note-1", "owner-1");
            when(noteRepository.findById("note-1")).thenReturn(Optional.of(note));

            assertFalse(noteService.removeCollaborator("note-1", "user-2", "not-owner"));
        }

        @Test
        void whenCollaboratorNotOnList_returnsFalse() {
            Note note = createNote("note-1", "owner-1");
            when(noteRepository.findById("note-1")).thenReturn(Optional.of(note));
            when(collaboratorRepository.existsByNoteIdAndUserId("note-1", "not-collab")).thenReturn(false);

            assertFalse(noteService.removeCollaborator("note-1", "not-collab", "owner-1"));
            verify(collaboratorRepository, never()).deleteByNoteIdAndUserId(any(), any());
        }
    }

    @Nested
    class GetNoteCollaboratorsEdgeCases {

        @Test
        void whenNoteNotFound_returnsEmptyList() {
            when(noteRepository.findById("missing")).thenReturn(Optional.empty());

            List<String> result = noteService.getNoteCollaborators("missing", "user-1");

            assertTrue(result.isEmpty());
        }

        @Test
        void whenOwner_returnsCollaboratorIds() {
            Note note = createNote("note-1", "owner-1");
            User collabUser = createUser("collab-1", "c@e.com");
            Collaborator collaborator = new Collaborator(note, collabUser);

            when(noteRepository.findById("note-1")).thenReturn(Optional.of(note));
            when(collaboratorRepository.findByNoteId("note-1")).thenReturn(List.of(collaborator));

            List<String> result = noteService.getNoteCollaborators("note-1", "owner-1");

            assertEquals(1, result.size());
            assertEquals("collab-1", result.get(0));
        }

        @Test
        void whenCollaborator_canViewOtherCollaborators() {
            Note note = createNote("note-1", "owner-1");
            User collabUser1 = createUser("collab-1", "c1@e.com");
            User collabUser2 = createUser("collab-2", "c2@e.com");
            Collaborator c1 = new Collaborator(note, collabUser1);
            Collaborator c2 = new Collaborator(note, collabUser2);

            when(noteRepository.findById("note-1")).thenReturn(Optional.of(note));
            when(collaboratorRepository.existsByNoteIdAndUserId("note-1", "collab-1")).thenReturn(true);
            when(collaboratorRepository.findByNoteId("note-1")).thenReturn(List.of(c1, c2));

            List<String> result = noteService.getNoteCollaborators("note-1", "collab-1");

            assertEquals(2, result.size());
        }
    }

    @Nested
    class HasNoteAccess {

        @Test
        void whenNoteNotFound_returnsFalse() {
            when(noteRepository.findById("missing")).thenReturn(Optional.empty());

            assertFalse(noteService.hasNoteAccess("missing", "user-1"));
        }
    }

    @Nested
    class GetNoteByIdEdgeCases {

        @Test
        void whenAccessGrantedButNoteNotInRepo_returnsNull() {
            when(selfProxy.hasNoteAccess("note-1", "user-1")).thenReturn(true);
            when(noteRepository.findById("note-1")).thenReturn(Optional.empty());

            assertNull(noteService.getNoteById("note-1", "user-1"));
        }
    }

    @Nested
    class CreateNoteEdgeCases {

        @Test
        void createdNote_hasNewUuidAndTimestamps() {
            NoteDTO request = new NoteDTO();
            request.setTitle("New Note");
            request.setContent("Some content");

            when(noteRepository.save(any(Note.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(collaboratorRepository.findByNoteId(any())).thenReturn(List.of());

            NoteDTO created = noteService.createNote(request, "user-1");

            assertNotNull(created.getId());
            assertNotNull(created.getCreatedAt());
            assertNotNull(created.getUpdatedAt());
            assertEquals("user-1", created.getOwnerId());
        }

        @Test
        void createdNote_hasEmptyCollaboratorList() {
            NoteDTO request = new NoteDTO();
            request.setTitle("Title");
            request.setContent("Content");

            when(noteRepository.save(any(Note.class)))
                    .thenAnswer(invocation -> invocation.getArgument(0));
            when(collaboratorRepository.findByNoteId(any())).thenReturn(List.of());

            NoteDTO created = noteService.createNote(request, "user-1");

            assertNotNull(created.getCollaboratorIds());
            assertTrue(created.getCollaboratorIds().isEmpty());
        }
    }

    @Nested
    class EvictNoteAccessCache {

        @Test
        void whenCacheIsNull_doesNotThrow() {
            Note note = createNote("note-1", "owner-1");
            User collabUser = createUser("user-2", "u@e.com");

            when(noteRepository.findById("note-1")).thenReturn(Optional.of(note));
            when(collaboratorRepository.existsByNoteIdAndUserId("note-1", "user-2")).thenReturn(false);
            when(userRepository.findById("user-2")).thenReturn(Optional.of(collabUser));
            when(cacheManager.getCache("noteAccessCache")).thenReturn(null);

            // Should not throw even when cache is null
            assertTrue(noteService.addCollaborator("note-1", "user-2", "owner-1"));
        }
    }
}
