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

import com.collabnotes.collabnotes.dto.InviteRequest;
import com.collabnotes.collabnotes.dto.NoteDTO;
import com.collabnotes.collabnotes.exception.ResourceNotFoundException;
import com.collabnotes.collabnotes.exception.UnauthorizedException;
import com.collabnotes.collabnotes.service.NoteService;

@RestController
@RequestMapping({"/api/notes", "/notes"})
public class NoteController {

    private static final String UNAUTHORIZED_MSG = "Unauthorized";
    private static final String NOT_FOUND_MSG = "Note not found or no permission";

    private final NoteService noteService;

    public NoteController(NoteService noteService) {
        this.noteService = noteService;
    }

    @PostMapping
    public ResponseEntity<NoteDTO> createNote(@RequestBody NoteDTO noteDTO, Authentication authentication) {
        String userId = getUserIdFromAuthenticationOrThrow(authentication);
        NoteDTO createdNote = noteService.createNote(noteDTO, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(createdNote);
    }

    @GetMapping("/{id}")
    public ResponseEntity<NoteDTO> getNoteById(@PathVariable("id") String id, Authentication authentication) {
        String userId = getUserIdFromAuthenticationOrThrow(authentication);
        NoteDTO note = noteService.getNoteById(id, userId);
        if (note == null) {
            throw new ResourceNotFoundException("Note not found");
        }
        return ResponseEntity.ok(note);
    }

    @GetMapping
    public ResponseEntity<List<NoteDTO>> getAllNotes(Authentication authentication) {
        String userId = getUserIdFromAuthenticationOrThrow(authentication);
        List<NoteDTO> notes = noteService.getAllNotesByUser(userId);
        return ResponseEntity.ok(notes);
    }

    @PutMapping("/{id}")
    public ResponseEntity<NoteDTO> updateNote(
            @PathVariable("id") String id,
            @RequestBody NoteDTO noteDTO,
            Authentication authentication) {
        String userId = getUserIdFromAuthenticationOrThrow(authentication);
        NoteDTO updatedNote = noteService.updateNote(id, noteDTO, userId);
        if (updatedNote == null) {
            throw new ResourceNotFoundException(NOT_FOUND_MSG);
        }
        return ResponseEntity.ok(updatedNote);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<String> deleteNote(@PathVariable("id") String id, Authentication authentication) {
        String userId = getUserIdFromAuthenticationOrThrow(authentication);
        boolean deleted = noteService.deleteNote(id, userId);
        if (!deleted) {
            throw new ResourceNotFoundException(NOT_FOUND_MSG);
        }
        return ResponseEntity.ok("Note deleted successfully");
    }

    @PostMapping("/{id}/invite")
    public ResponseEntity<String> inviteCollaborator(
            @PathVariable("id") String id,
            @RequestBody InviteRequest inviteRequest,
            Authentication authentication) {
        String userId = getUserIdFromAuthenticationOrThrow(authentication);
        boolean invited = noteService.inviteCollaboratorByEmail(id, inviteRequest.getEmail(), userId);
        if (!invited) {
            throw new ResourceNotFoundException(NOT_FOUND_MSG);
        }
        return ResponseEntity.ok("Collaborator invited successfully");
    }

    @PostMapping("/{id}/collaborators/{collaboratorId}")
    public ResponseEntity<String> addCollaborator(
            @PathVariable("id") String id,
            @PathVariable("collaboratorId") String collaboratorId,
            Authentication authentication) {
        String userId = getUserIdFromAuthenticationOrThrow(authentication);
        boolean added = noteService.addCollaborator(id, collaboratorId, userId);
        if (!added) {
            throw new ResourceNotFoundException(NOT_FOUND_MSG);
        }
        return ResponseEntity.ok("Collaborator added successfully");
    }

    @DeleteMapping("/{id}/collaborators/{collaboratorId}")
    public ResponseEntity<String> removeCollaborator(
            @PathVariable("id") String id,
            @PathVariable("collaboratorId") String collaboratorId,
            Authentication authentication) {
        String userId = getUserIdFromAuthenticationOrThrow(authentication);
        boolean removed = noteService.removeCollaborator(id, collaboratorId, userId);
        if (!removed) {
            throw new ResourceNotFoundException(NOT_FOUND_MSG);
        }
        return ResponseEntity.ok("Collaborator removed successfully");
    }

    private String getUserIdFromAuthenticationOrThrow(Authentication authentication) {
        if (authentication == null) {
            throw new UnauthorizedException(UNAUTHORIZED_MSG);
        }
        if (authentication.getPrincipal() instanceof Jwt jwt) {
            return jwt.getSubject();
        }
        return authentication.getName();
    }
}
