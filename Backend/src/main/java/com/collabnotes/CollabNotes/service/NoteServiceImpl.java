package com.collabnotes.CollabNotes.service;

import com.collabnotes.CollabNotes.dto.NoteDTO;
import com.collabnotes.CollabNotes.metrics.MetricsService;
import com.google.cloud.firestore.DocumentReference;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.cloud.firestore.Firestore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutionException;

@Service
public class NoteServiceImpl implements NoteService {

    private static final Logger logger = LoggerFactory.getLogger(NoteServiceImpl.class);
    private static final String COLLECTION_NAME = "notes";

    @Autowired
    private Firestore firestore;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private PubSubPublisherService pubSubPublisherService;

    @Autowired
    private MetricsService metricsService;


    @Override
    public NoteDTO createNote(NoteDTO noteDTO, String userId) throws ExecutionException, InterruptedException {
        // Set owner and timestamps
        noteDTO.setOwnerId(userId);
        Date now = new Date();
        noteDTO.setCreatedAt(now);
        noteDTO.setUpdatedAt(now);

        if (noteDTO.getCollaboratorIds() == null) {
            noteDTO.setCollaboratorIds(new ArrayList<>());
        }

        // Convert to map for Firestore
        Map<String, Object> noteMap = convertNoteToMap(noteDTO);

        // Add to Firestore
        DocumentReference docRef = firestore.collection(COLLECTION_NAME).document();
        docRef.set(noteMap).get();

        // Set the ID and return
        noteDTO.setId(docRef.getId());

        pubSubPublisherService.publishNoteUpdate(noteDTO.getId(), userId, "create");

        return noteDTO;
    }

    @Override
    public NoteDTO getNoteById(String id, String userId) throws ExecutionException, InterruptedException {

        if (!hasNoteAccess(id, userId)) {
            return null;
        }
        DocumentSnapshot document = firestore.collection(COLLECTION_NAME).document(id).get().get();
        if (!document.exists()) {
            return null;
        }

        if (!document.exists()) {
            return null;
        }

        NoteDTO note = convertMapToNote(document.getData(), document.getId());

        if (!userId.equals(note.getOwnerId()) && !note.getCollaboratorIds().contains(userId)) {
            logger.warn("User {} attempted to access note {} without permission", userId, id);
            return null;
        }

        return note;
    }

    @Override
    public List<NoteDTO> getAllNotesByUser(String userId) throws ExecutionException, InterruptedException {
        List<NoteDTO> notes = new ArrayList<>();

        // Get notes where user is owner
        firestore.collection(COLLECTION_NAME)
                .whereEqualTo("ownerId", userId)
                .get()
                .get()
                .getDocuments()
                .forEach(doc -> notes.add(convertMapToNote(doc.getData(), doc.getId())));

        // Get notes where user is collaborator
        firestore.collection(COLLECTION_NAME)
                .whereArrayContains("collaboratorIds", userId)
                .get()
                .get()
                .getDocuments()
                .forEach(doc -> notes.add(convertMapToNote(doc.getData(), doc.getId())));

        return notes;
    }

    @Override
    public NoteDTO updateNote(String id, NoteDTO noteDTO, String userId) throws ExecutionException, InterruptedException {
        // Get existing note
        DocumentSnapshot document = firestore.collection(COLLECTION_NAME).document(id).get().get();

        if (!document.exists()) {
            return null;
        }

        NoteDTO existingNote = convertMapToNote(document.getData(), document.getId());

        // Verify user has permission to update (owner or collaborator)
        if (!userId.equals(existingNote.getOwnerId()) && !existingNote.getCollaboratorIds().contains(userId)) {
            logger.warn("User {} attempted to update note {} without permission", userId, id);
            return null;
        }

        // Preserve owner and creation date
        noteDTO.setOwnerId(existingNote.getOwnerId());
        noteDTO.setCreatedAt(existingNote.getCreatedAt());
        noteDTO.setUpdatedAt(new Date());
        noteDTO.setId(id);

        // If collaboratorIds is null, keep existing ones
        if (noteDTO.getCollaboratorIds() == null) {
            noteDTO.setCollaboratorIds(existingNote.getCollaboratorIds());
        }

        // Update in Firestore
        Map<String, Object> updateData = convertNoteToMap(noteDTO);
        firestore.collection(COLLECTION_NAME).document(id).set(updateData).get();

        // After successful update
        pubSubPublisherService.publishNoteUpdate(id, userId, "update");

        return noteDTO;
    }

    @Override
    public boolean deleteNote(String id, String userId) throws ExecutionException, InterruptedException {
        // Get existing note
        DocumentSnapshot document = firestore.collection(COLLECTION_NAME).document(id).get().get();

        if (!document.exists()) {
            return false;
        }

        NoteDTO existingNote = convertMapToNote(document.getData(), document.getId());

        // Only owner can delete
        if (!userId.equals(existingNote.getOwnerId())) {
            logger.warn("User {} attempted to delete note {} without permission", userId, id);
            return false;
        }

        // Delete from Firestore
        firestore.collection(COLLECTION_NAME).document(id).delete().get();
        return true;
    }

    @Override
    public boolean addCollaborator(String noteId, String collaboratorId, String userId) throws ExecutionException, InterruptedException {
        logger.info("Adding collaborator {} to note {} by user {}", collaboratorId, noteId, userId);
        DocumentSnapshot document = firestore.collection(COLLECTION_NAME).document(noteId).get().get();

        if (!document.exists()) {
            logger.warn("Note {} not found", noteId);
            return false;
        }

        NoteDTO existingNote = convertMapToNote(document.getData(), document.getId());

        // Debug the note data
        logger.debug("Note data for {}: {}", noteId, document.getData());
        logger.debug("Converted note: id={}, ownerId={}, collaborators={}",
                existingNote.getId(), existingNote.getOwnerId(), existingNote.getCollaboratorIds());

        // Only owner can add collaborators
        if (!userId.equals(existingNote.getOwnerId())) {
            logger.warn("User {} attempted to add collaborator to note {} without permission", userId, noteId);
            return false;
        }

        // Initialize collaborators list if null
        List<String> collaborators = existingNote.getCollaboratorIds();
        if (collaborators == null) {
            collaborators = new ArrayList<>();
            existingNote.setCollaboratorIds(collaborators);
        }

        // Don't add if collaborator is already the owner
        if (collaboratorId.equals(existingNote.getOwnerId())) {
            logger.info("Collaborator {} is already the owner of note {}", collaboratorId, noteId);
            return false;
        }

        // Add collaborator if not already present
        if (!collaborators.contains(collaboratorId)) {
            logger.info("Adding {} to collaborators list of note {}", collaboratorId, noteId);
            collaborators.add(collaboratorId);

            // Update in Firestore
            Map<String, Object> updates = new HashMap<>();
            updates.put("collaboratorIds", collaborators);
            updates.put("updatedAt", new Date());

            firestore.collection(COLLECTION_NAME).document(noteId)
                    .update(updates)
                    .get();

            logger.info("Successfully added collaborator {} to note {}", collaboratorId, noteId);
            notifyCollaborators(noteId, userId, "collaborator_added");
            pubSubPublisherService.publishNoteUpdate(noteId, userId, "collaborator_added");
            return true;
        } else {
            logger.info("Collaborator {} is already on the list for note {}", collaboratorId, noteId);
            return false;
        }
    }

    @Override
    public boolean removeCollaborator(String noteId, String collaboratorId, String userId) throws ExecutionException, InterruptedException {
        logger.info("Removing collaborator {} from note {} by user {}", collaboratorId, noteId, userId);
        // Get existing note
        DocumentSnapshot document = firestore.collection(COLLECTION_NAME).document(noteId).get().get();

        if (!document.exists()) {
            logger.warn("Note {} not found", noteId);
            return false;
        }

        NoteDTO existingNote = convertMapToNote(document.getData(), document.getId());

        // Only owner can remove collaborators
        if (!userId.equals(existingNote.getOwnerId())) {
            logger.warn("User {} attempted to remove collaborator from note {} without permission", userId, noteId);
            return false;
        }

        // Remove collaborator if present
        List<String> collaborators = existingNote.getCollaboratorIds();
        if (collaborators != null && collaborators.contains(collaboratorId)) {
            collaborators.remove(collaboratorId);

            // Update in Firestore
            Map<String, Object> updates = new HashMap<>();
            updates.put("collaboratorIds", collaborators);
            updates.put("updatedAt", new Date());

            firestore.collection(COLLECTION_NAME).document(noteId)
                    .update(updates)
                    .get();

            logger.info("Successfully removed collaborator {} from note {}", collaboratorId, noteId);
            notifyCollaborators(noteId, userId, "collaborator_removed");
            pubSubPublisherService.publishNoteUpdate(noteId, userId, "collaborator_removed");
            return true;
        }

        logger.info("Collaborator {} was not found on the list for note {}", collaboratorId, noteId);
        return false;
    }

    @Override
    public List<String> getNoteCollaborators(String noteId, String userId) throws ExecutionException, InterruptedException {
        logger.info("Retrieving collaborators for note {} by user {}", noteId, userId);

        // Get the note document from Firestore
        DocumentSnapshot document = firestore.collection(COLLECTION_NAME).document(noteId).get().get();

        if (!document.exists()) {
            logger.warn("Note {} not found when retrieving collaborators", noteId);
            return Collections.emptyList();
        }

        // Convert document to note DTO
        NoteDTO note = convertMapToNote(document.getData(), document.getId());

        // Debug the note data
        logger.debug("Note data for {}: {}", noteId, document.getData());
        logger.debug("Retrieved note: id={}, ownerId={}, collaborators={}",
                note.getId(), note.getOwnerId(), note.getCollaboratorIds());

        // Check if user has access to view collaborators (must be owner or collaborator)
        boolean isOwner = userId.equals(note.getOwnerId());
        boolean isCollaborator = note.getCollaboratorIds() != null &&
                note.getCollaboratorIds().contains(userId);

        if (!isOwner && !isCollaborator) {
            logger.warn("User {} attempted to view collaborators of note {} without permission", userId, noteId);
            return Collections.emptyList();
        }

        // Return the collaborator list, or empty list if null
        List<String> collaborators = note.getCollaboratorIds();
        if (collaborators == null) {
            logger.info("No collaborators found for note {}", noteId);
            return Collections.emptyList();
        }

        logger.info("Successfully retrieved {} collaborators for note {}", collaborators.size(), noteId);
        return new ArrayList<>(collaborators); // Return a copy to prevent modification of internal state
    }

    // Helper methods for conversion between DTO and Map
    private Map<String, Object> convertNoteToMap(NoteDTO note) {
        Map<String, Object> map = new HashMap<>();
        map.put("title", note.getTitle());
        map.put("content", note.getContent());
        map.put("ownerId", note.getOwnerId());
        map.put("collaboratorIds", note.getCollaboratorIds());
        map.put("createdAt", note.getCreatedAt());
        map.put("updatedAt", note.getUpdatedAt());
        return map;
    }

    private NoteDTO convertMapToNote(Map<String, Object> map, String id) {
        NoteDTO note = new NoteDTO();
        note.setId(id);
        note.setTitle((String) map.get("title"));
        note.setContent((String) map.get("content"));
        note.setOwnerId((String) map.get("ownerId"));

        // Handle collaboratorIds cast safely
        @SuppressWarnings("unchecked")
        List<String> collaborators = (List<String>) map.get("collaboratorIds");
        note.setCollaboratorIds(collaborators != null ? collaborators : new ArrayList<>());

        // Handle dates
        note.setCreatedAt(map.get("createdAt") instanceof Date ? (Date) map.get("createdAt") : null);
        note.setUpdatedAt(map.get("updatedAt") instanceof Date ? (Date) map.get("updatedAt") : null);

        if (map.containsKey("analysis")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> analysis = (Map<String, Object>) map.get("analysis");
            note.setAnalysis(analysis);
        }

        return note;
    }

    private void notifyCollaborators(String noteId, String userId, String action) {
        Map<String, Object> notification = new HashMap<>();
        notification.put("action", action);
        notification.put("userId", userId);
        notification.put("timestamp", new Date());

        messagingTemplate.convertAndSend("/topic/notes/" + noteId + "/events", notification);
    }

    @Cacheable(value = "noteAccessCache", key = "#noteId + '-' + #userId")
    public boolean hasNoteAccess(String noteId, String userId) throws ExecutionException, InterruptedException {
        DocumentSnapshot document = firestore.collection(COLLECTION_NAME).document(noteId).get().get();

        if (!document.exists()) {
            return false;
        }

        NoteDTO note = convertMapToNote(document.getData(), document.getId());

        // Check if user is owner or collaborator
        return userId.equals(note.getOwnerId()) ||
                (note.getCollaboratorIds() != null && note.getCollaboratorIds().contains(userId));
    }


}