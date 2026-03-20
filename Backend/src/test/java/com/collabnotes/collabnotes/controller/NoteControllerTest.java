package com.collabnotes.collabnotes.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.Date;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.collabnotes.collabnotes.dto.NoteDTO;
import com.collabnotes.collabnotes.exception.GlobalExceptionHandler;
import com.collabnotes.collabnotes.service.NoteService;

@ExtendWith(MockitoExtension.class)
class NoteControllerTest {

    @Mock
    private NoteService noteService;

    private MockMvc mockMvc;
    private JwtAuthenticationToken authToken;

    @BeforeEach
    void setUp() {
        NoteController controller = new NoteController(noteService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();

        Jwt jwt = Jwt.withTokenValue("mock-token")
                .header("alg", "none")
                .subject("test-user")
                .claim("email", "test@example.com")
                .issuedAt(Instant.now())
                .expiresAt(Instant.now().plusSeconds(3600))
                .build();
        authToken = new JwtAuthenticationToken(jwt);
    }

    /** Helper to attach the JWT principal to a request builder. */
    private MockHttpServletRequestBuilder withAuth(MockHttpServletRequestBuilder builder) {
        return builder.principal(authToken);
    }

    private static NoteDTO createSampleNote(String id, String ownerId) {
        NoteDTO dto = new NoteDTO();
        dto.setId(id);
        dto.setTitle("Test Note");
        dto.setContent("Test Content");
        dto.setOwnerId(ownerId);
        dto.setCollaboratorIds(List.of());
        dto.setVersion(1L);
        dto.setCreatedAt(new Date());
        dto.setUpdatedAt(new Date());
        return dto;
    }

    @Nested
    class CreateNote {

        @Test
        void whenAuthenticated_returns201WithCreatedNote() throws Exception {
            NoteDTO created = createSampleNote("note-1", "test-user");
            when(noteService.createNote(any(NoteDTO.class), eq("test-user"))).thenReturn(created);

            mockMvc.perform(withAuth(post("/api/notes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Test Note\",\"content\":\"Test Content\"}")))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value("note-1"))
                    .andExpect(jsonPath("$.title").value("Test Note"))
                    .andExpect(jsonPath("$.ownerId").value("test-user"));
        }

        @Test
        void whenNotAuthenticated_returns401() throws Exception {
            mockMvc.perform(post("/api/notes")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"T\",\"content\":\"C\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class GetNoteById {

        @Test
        void whenNoteExists_returns200WithNote() throws Exception {
            NoteDTO note = createSampleNote("note-1", "test-user");
            when(noteService.getNoteById("note-1", "test-user")).thenReturn(note);

            mockMvc.perform(withAuth(get("/api/notes/note-1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.id").value("note-1"))
                    .andExpect(jsonPath("$.title").value("Test Note"));
        }

        @Test
        void whenNoteNotFound_returns404() throws Exception {
            when(noteService.getNoteById("missing", "test-user")).thenReturn(null);

            mockMvc.perform(withAuth(get("/api/notes/missing")))
                    .andExpect(status().isNotFound());
        }

        @Test
        void whenNotAuthenticated_returns401() throws Exception {
            mockMvc.perform(get("/api/notes/note-1"))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Nested
    class GetAllNotes {

        @Test
        void returnsListOfNotes() throws Exception {
            NoteDTO note1 = createSampleNote("note-1", "test-user");
            NoteDTO note2 = createSampleNote("note-2", "test-user");
            when(noteService.getAllNotesByUser("test-user")).thenReturn(List.of(note1, note2));

            mockMvc.perform(withAuth(get("/api/notes")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2))
                    .andExpect(jsonPath("$[0].id").value("note-1"))
                    .andExpect(jsonPath("$[1].id").value("note-2"));
        }

        @Test
        void whenNoNotes_returnsEmptyList() throws Exception {
            when(noteService.getAllNotesByUser("test-user")).thenReturn(List.of());

            mockMvc.perform(withAuth(get("/api/notes")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
    }

    @Nested
    class UpdateNote {

        @Test
        void whenOwnerUpdates_returns200WithUpdatedNote() throws Exception {
            NoteDTO updated = createSampleNote("note-1", "test-user");
            updated.setTitle("Updated Title");
            when(noteService.updateNote(eq("note-1"), any(NoteDTO.class), eq("test-user")))
                    .thenReturn(updated);

            mockMvc.perform(withAuth(put("/api/notes/note-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Updated Title\",\"content\":\"Content\"}")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.title").value("Updated Title"));
        }

        @Test
        void whenNoteNotFoundOrNoPermission_returns404() throws Exception {
            when(noteService.updateNote(eq("note-1"), any(NoteDTO.class), eq("test-user")))
                    .thenReturn(null);

            mockMvc.perform(withAuth(put("/api/notes/note-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Title\",\"content\":\"Content\"}")))
                    .andExpect(status().isNotFound());
        }

        @Test
        void whenVersionConflict_returns409() throws Exception {
            when(noteService.updateNote(eq("note-1"), any(NoteDTO.class), eq("test-user")))
                    .thenThrow(new com.collabnotes.collabnotes.exception.ConflictException(
                            "Note was modified by another user. Please refresh and try again."));

            mockMvc.perform(withAuth(put("/api/notes/note-1")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"title\":\"Title\",\"content\":\"Content\",\"version\":2}")))
                    .andExpect(status().isConflict());
        }
    }

    @Nested
    class DeleteNote {

        @Test
        void whenOwnerDeletes_returns200() throws Exception {
            when(noteService.deleteNote("note-1", "test-user")).thenReturn(true);

            mockMvc.perform(withAuth(delete("/api/notes/note-1")))
                    .andExpect(status().isOk());
        }

        @Test
        void whenNoteNotFoundOrNotOwner_returns404() throws Exception {
            when(noteService.deleteNote("note-1", "test-user")).thenReturn(false);

            mockMvc.perform(withAuth(delete("/api/notes/note-1")))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class InviteCollaborator {

        @Test
        void whenSuccessful_returns200() throws Exception {
            when(noteService.inviteCollaboratorByEmail("note-1", "friend@example.com", "test-user"))
                    .thenReturn(true);

            mockMvc.perform(withAuth(post("/api/notes/note-1/invite")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"friend@example.com\"}")))
                    .andExpect(status().isOk());
        }

        @Test
        void whenFailed_returns404() throws Exception {
            when(noteService.inviteCollaboratorByEmail("note-1", "nobody@example.com", "test-user"))
                    .thenReturn(false);

            mockMvc.perform(withAuth(post("/api/notes/note-1/invite")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"nobody@example.com\"}")))
                    .andExpect(status().isNotFound());
        }

        @Test
        void whenUserNotFoundByEmail_returns400() throws Exception {
            when(noteService.inviteCollaboratorByEmail("note-1", "nobody@example.com", "test-user"))
                    .thenThrow(new IllegalArgumentException("User with email nobody@example.com not found"));

            mockMvc.perform(withAuth(post("/api/notes/note-1/invite")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"nobody@example.com\"}")))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    class AddCollaborator {

        @Test
        void whenSuccessful_returns200() throws Exception {
            when(noteService.addCollaborator("note-1", "collab-1", "test-user"))
                    .thenReturn(true);

            mockMvc.perform(withAuth(post("/api/notes/note-1/collaborators/collab-1")))
                    .andExpect(status().isOk());
        }

        @Test
        void whenFailed_returns404() throws Exception {
            when(noteService.addCollaborator("note-1", "collab-1", "test-user"))
                    .thenReturn(false);

            mockMvc.perform(withAuth(post("/api/notes/note-1/collaborators/collab-1")))
                    .andExpect(status().isNotFound());
        }
    }

    @Nested
    class RemoveCollaborator {

        @Test
        void whenSuccessful_returns200() throws Exception {
            when(noteService.removeCollaborator("note-1", "collab-1", "test-user"))
                    .thenReturn(true);

            mockMvc.perform(withAuth(delete("/api/notes/note-1/collaborators/collab-1")))
                    .andExpect(status().isOk());
        }

        @Test
        void whenFailed_returns404() throws Exception {
            when(noteService.removeCollaborator("note-1", "collab-1", "test-user"))
                    .thenReturn(false);

            mockMvc.perform(withAuth(delete("/api/notes/note-1/collaborators/collab-1")))
                    .andExpect(status().isNotFound());
        }
    }
}
