package com.collabnotes.collabnotes.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.collabnotes.collabnotes.dto.NoteDTO;
import com.collabnotes.collabnotes.service.NoteService;

@RestController
@RequestMapping("/api/notes")
public class NoteController {

    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    @PostMapping
    public ResponseEntity<?> createNote(@RequestBody NoteDTO noteDTO, Authentication authentication) {
        try {
            String userId = getUserIdFromAuthentication(authentication);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }

            NoteDTO createdNote = noteService.createNote(noteDTO, userId);
            return ResponseEntity.status(HttpStatus.CREATED).body(createdNote);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error creating note: " + e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getNoteById(@PathVariable String id, Authentication authentication) {
        try {
            String userId = getUserIdFromAuthentication(authentication);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }

            NoteDTO note = noteService.getNoteById(id, userId);
            if (note == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Note not found");
            }

            return ResponseEntity.ok(note);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error retrieving note: " + e.getMessage());
        }
    }

    @GetMapping
    public ResponseEntity<?> getAllNotes(Authentication authentication) {
        try {
            String userId = getUserIdFromAuthentication(authentication);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }

            List<NoteDTO> notes = noteService.getAllNotesByUser(userId);
            return ResponseEntity.ok(notes);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error retrieving notes: " + e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> updateNote(
            @PathVariable String id,
            @RequestBody NoteDTO noteDTO,
            Authentication authentication) {
        try {
            String userId = getUserIdFromAuthentication(authentication);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }

            NoteDTO updatedNote = noteService.updateNote(id, noteDTO, userId);
            if (updatedNote == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Note not found or no permission");
            }

            return ResponseEntity.ok(updatedNote);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error updating note: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> deleteNote(@PathVariable String id, Authentication authentication) {
        try {
            String userId = getUserIdFromAuthentication(authentication);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }

            boolean deleted = noteService.deleteNote(id, userId);
            if (!deleted) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Note not found or no permission");
            }

            return ResponseEntity.ok("Note deleted successfully");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error deleting note: " + e.getMessage());
        }
    }

    @PostMapping("/{id}/collaborators/{collaboratorId}")
    public ResponseEntity<?> addCollaborator(
            @PathVariable String id,
            @PathVariable String collaboratorId,
            Authentication authentication) {
        try {
            String userId = getUserIdFromAuthentication(authentication);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }

            boolean added = noteService.addCollaborator(id, collaboratorId, userId);
            if (!added) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Note not found or no permission");
            }

            return ResponseEntity.ok("Collaborator added successfully");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error adding collaborator: " + e.getMessage());
        }
    }

    @DeleteMapping("/{id}/collaborators/{collaboratorId}")
    public ResponseEntity<?> removeCollaborator(
            @PathVariable String id,
            @PathVariable String collaboratorId,
            Authentication authentication) {
        try {
            String userId = getUserIdFromAuthentication(authentication);
            if (userId == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Unauthorized");
            }

            boolean removed = noteService.removeCollaborator(id, collaboratorId, userId);
            if (!removed) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Note not found or no permission");
            }

            return ResponseEntity.ok("Collaborator removed successfully");
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error removing collaborator: " + e.getMessage());
        }
    }

    private String getUserIdFromAuthentication(Authentication authentication) {
        if (authentication == null) {
            return null;
        }
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();
        }
        return authentication.getName();
    }
}
