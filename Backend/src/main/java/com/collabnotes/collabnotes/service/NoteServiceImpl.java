package com.collabnotes.collabnotes.service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.lang.NonNull;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.collabnotes.collabnotes.dto.NoteDTO;
import com.collabnotes.collabnotes.entity.Collaborator;
import com.collabnotes.collabnotes.entity.Note;
import com.collabnotes.collabnotes.entity.User;
import com.collabnotes.collabnotes.repository.CollaboratorRepository;
import com.collabnotes.collabnotes.repository.NoteRepository;
import com.collabnotes.collabnotes.repository.UserRepository;

@Service
public class NoteServiceImpl implements NoteService {

    private static final Logger logger = LoggerFactory.getLogger(NoteServiceImpl.class);

    private final NoteRepository noteRepository;
    private final UserRepository userRepository;
    private final CollaboratorRepository collaboratorRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final NoteEventPublisher noteEventPublisher;
    private final NoteServiceImpl self;
    private final CacheManager cacheManager;

    public NoteServiceImpl(
            NoteRepository noteRepository,
            UserRepository userRepository,
            CollaboratorRepository collaboratorRepository,
            SimpMessagingTemplate messagingTemplate,
            NoteEventPublisher noteEventPublisher,
            CacheManager cacheManager,
            @Lazy NoteServiceImpl self) {
        this.noteRepository = noteRepository;
        this.userRepository = userRepository;
        this.collaboratorRepository = collaboratorRepository;
        this.messagingTemplate = messagingTemplate;
        this.noteEventPublisher = noteEventPublisher;
        this.cacheManager = cacheManager;
        this.self = self;
    }

    @Override
    @Transactional
    public NoteDTO createNote(NoteDTO noteDTO, String userId) {
        Note note = new Note();
        note.setId(UUID.randomUUID().toString());
        note.setTitle(noteDTO.getTitle());
        note.setContent(noteDTO.getContent());
        note.setOwnerId(userId);
        note.setCreatedAt(LocalDateTime.now());
        note.setUpdatedAt(LocalDateTime.now());

        note = noteRepository.save(note);

        noteEventPublisher.publishNoteUpdate(note.getId(), userId, "create");

        return convertToDTO(note);
    }

    @Override
    @Transactional(readOnly = true)
    public NoteDTO getNoteById(@NonNull String id, String userId) {
        if (!self.hasNoteAccess(id, userId)) {
            return null;
        }

        return noteRepository.findById(id)
                .map(this::convertToDTO)
                .orElse(null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<NoteDTO> getAllNotesByUser(String userId) {
        List<NoteDTO> notes = new ArrayList<>();

        notes.addAll(noteRepository.findByOwnerId(userId).stream()
                .map(this::convertToDTO)
                .toList());

        notes.addAll(noteRepository.findByCollaboratorUserId(userId).stream()
                .map(this::convertToDTO)
                .toList());

        return notes;
    }

    @Override
    @Transactional
    public NoteDTO updateNote(String id, NoteDTO noteDTO, String userId) {
        if (id == null) {
            return null;
        }
        Note note = noteRepository.findById(id).orElse(null);

        if (note == null) {
            return null;
        }

        if (!userId.equals(note.getOwnerId()) && !isCollaborator(id, userId)) {
            logger.warn("User {} attempted to update note {} without permission", userId, id);
            return null;
        }

        note.setTitle(noteDTO.getTitle());
        note.setContent(noteDTO.getContent());
        note.setUpdatedAt(LocalDateTime.now());

        if (noteDTO.getAnalysis() != null) {
            note.setAnalysis(noteDTO.getAnalysis().toString());
        }

        note = noteRepository.save(note);

        noteEventPublisher.publishNoteUpdate(id, userId, "update");

        return convertToDTO(note);
    }

    @Override
    @Transactional
    public boolean deleteNote(String id, String userId) {
        if (id == null) {
            return false;
        }
        Note note = noteRepository.findById(id).orElse(null);

        if (note == null) {
            return false;
        }

        if (!userId.equals(note.getOwnerId())) {
            logger.warn("User {} attempted to delete note {} without permission", userId, id);
            return false;
        }

        noteRepository.delete(note);
        return true;
    }

    @Override
    @Transactional
    public boolean inviteCollaboratorByEmail(String noteId, String email, String userId) {
        logger.info("Inviting collaborator {} to note {} by user {}", email, noteId, userId);
        User collaboratorUser = userRepository.findByEmail(email).orElse(null);
        if (collaboratorUser == null) {
            logger.warn("User with email {} not found", email);
            throw new IllegalArgumentException("User with email " + email + " not found");
        }
        return self.addCollaborator(noteId, collaboratorUser.getId(), userId);
    }

    @Override
    @Transactional
    public boolean addCollaborator(String noteId, String collaboratorId, String userId) {
        logger.info("Adding collaborator {} to note {} by user {}", collaboratorId, noteId, userId);

        Note note = noteRepository.findById(noteId).orElse(null);
        if (note == null) {
            logger.warn("Note {} not found", noteId);
            return false;
        }

        if (!userId.equals(note.getOwnerId())) {
            logger.warn("User {} attempted to add collaborator to note {} without permission", userId, noteId);
            return false;
        }

        if (collaboratorId.equals(note.getOwnerId())) {
            logger.info("Collaborator {} is already the owner of note {}", collaboratorId, noteId);
            return false;
        }

        if (collaboratorRepository.existsByNoteIdAndUserId(noteId, collaboratorId)) {
            logger.info("Collaborator {} is already on the list for note {}", collaboratorId, noteId);
            return false;
        }

        User collaboratorUser = userRepository.findById(collaboratorId).orElse(null);
        if (collaboratorUser == null) {
            logger.warn("User {} not found", collaboratorId);
            return false;
        }

        Collaborator collaborator = new Collaborator(note, collaboratorUser);
        collaboratorRepository.save(collaborator);

        note.setUpdatedAt(LocalDateTime.now());
        noteRepository.save(note);

        logger.info("Successfully added collaborator {} to note {}", collaboratorId, noteId);
        evictNoteAccessCacheForUsers(noteId, userId, collaboratorId, note.getOwnerId());
        notifyCollaborators(noteId, userId, "collaborator_added");
        noteEventPublisher.publishNoteUpdate(noteId, userId, "collaborator_added");

        return true;
    }

    @Override
    @Transactional
    public boolean removeCollaborator(String noteId, String collaboratorId, String userId) {
        logger.info("Removing collaborator {} from note {} by user {}", collaboratorId, noteId, userId);

        Note note = noteRepository.findById(noteId).orElse(null);
        if (note == null) {
            logger.warn("Note {} not found", noteId);
            return false;
        }

        if (!userId.equals(note.getOwnerId())) {
            logger.warn("User {} attempted to remove collaborator from note {} without permission", userId, noteId);
            return false;
        }

        if (collaboratorRepository.existsByNoteIdAndUserId(noteId, collaboratorId)) {
            collaboratorRepository.deleteByNoteIdAndUserId(noteId, collaboratorId);

            note.setUpdatedAt(LocalDateTime.now());
            noteRepository.save(note);

            logger.info("Successfully removed collaborator {} from note {}", collaboratorId, noteId);
            evictNoteAccessCacheForUsers(noteId, userId, collaboratorId, note.getOwnerId());
            notifyCollaborators(noteId, userId, "collaborator_removed");
            noteEventPublisher.publishNoteUpdate(noteId, userId, "collaborator_removed");

            return true;
        }

        logger.info("Collaborator {} was not found on the list for note {}", collaboratorId, noteId);
        return false;
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> getNoteCollaborators(String noteId, String userId) {
        logger.info("Retrieving collaborators for note {} by user {}", noteId, userId);

        Note note = noteRepository.findById(noteId).orElse(null);
        if (note == null) {
            logger.warn("Note {} not found when retrieving collaborators", noteId);
            return List.of();
        }

        boolean isOwner = userId.equals(note.getOwnerId());
        boolean isCollaborator = isCollaborator(noteId, userId);

        if (!isOwner && !isCollaborator) {
            logger.warn("User {} attempted to view collaborators of note {} without permission", userId, noteId);
            return List.of();
        }

        List<Collaborator> collaborators = collaboratorRepository.findByNoteId(noteId);
        List<String> collaboratorIds = collaborators.stream()
                .map(c -> c.getUser().getId())
                .toList();

        logger.info("Successfully retrieved {} collaborators for note {}", collaboratorIds.size(), noteId);
        return collaboratorIds;
    }

    @Cacheable(value = "noteAccessCache", key = "#p0 + '-' + #p1")
    @Transactional(readOnly = true)
    public boolean hasNoteAccess(String noteId, String userId) {
        Note note = noteRepository.findById(noteId).orElse(null);

        if (note == null) {
            return false;
        }

        return userId.equals(note.getOwnerId()) || isCollaborator(noteId, userId);
    }

    private boolean isCollaborator(String noteId, String userId) {
        return collaboratorRepository.existsByNoteIdAndUserId(noteId, userId);
    }

    private void evictNoteAccessCacheForUsers(String noteId, String... userIds) {
        Cache noteAccessCache = cacheManager.getCache("noteAccessCache");
        if (noteAccessCache == null || userIds == null) {
            return;
        }

        for (String userId : userIds) {
            if (userId == null || userId.isBlank()) {
                continue;
            }
            noteAccessCache.evict(noteId + "-" + userId);
        }
    }

    private NoteDTO convertToDTO(Note note) {
        NoteDTO dto = new NoteDTO();
        dto.setId(note.getId());
        dto.setTitle(note.getTitle());
        dto.setContent(note.getContent());
        dto.setOwnerId(note.getOwnerId());
        dto.setCreatedAt(java.util.Date.from(note.getCreatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant()));
        dto.setUpdatedAt(note.getUpdatedAt() != null
                ? java.util.Date.from(note.getUpdatedAt().atZone(java.time.ZoneId.systemDefault()).toInstant())
                : null);

        List<Collaborator> collaborators = collaboratorRepository.findByNoteId(note.getId());
        dto.setCollaboratorIds(collaborators.stream().map(c -> c.getUser().getId()).toList());

        return dto;
    }

    private void notifyCollaborators(String noteId, String userId, String action) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("action", action);
        notification.put("userId", userId);
        notification.put("timestamp", LocalDateTime.now());

        messagingTemplate.convertAndSend("/topic/notes/" + noteId + "/events", (Object) notification);
    }
}
