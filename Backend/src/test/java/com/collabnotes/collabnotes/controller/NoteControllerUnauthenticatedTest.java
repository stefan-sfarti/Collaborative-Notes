package com.collabnotes.collabnotes.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.collabnotes.collabnotes.exception.GlobalExceptionHandler;
import com.collabnotes.collabnotes.service.NoteService;

/**
 * Tests that all NoteController endpoints reject unauthenticated requests
 * (when Authentication is null, the controller throws UnauthorizedException).
 */
@ExtendWith(MockitoExtension.class)
class NoteControllerUnauthenticatedTest {

    @Mock
    private NoteService noteService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
        NoteController controller = new NoteController(noteService);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void createNote_returns401() throws Exception {
        mockMvc.perform(post("/api/notes")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"T\",\"content\":\"C\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getNoteById_returns401() throws Exception {
        mockMvc.perform(get("/api/notes/note-1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getAllNotes_returns401() throws Exception {
        mockMvc.perform(get("/api/notes"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateNote_returns401() throws Exception {
        mockMvc.perform(put("/api/notes/note-1")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"T\",\"content\":\"C\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deleteNote_returns401() throws Exception {
        mockMvc.perform(delete("/api/notes/note-1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void inviteCollaborator_returns401() throws Exception {
        mockMvc.perform(post("/api/notes/note-1/invite")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"a@b.com\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void addCollaborator_returns401() throws Exception {
        mockMvc.perform(post("/api/notes/note-1/collaborators/collab-1"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void removeCollaborator_returns401() throws Exception {
        mockMvc.perform(delete("/api/notes/note-1/collaborators/collab-1"))
                .andExpect(status().isUnauthorized());
    }
}
