package com.collabnotes.CollabNotes.websocket;

import com.collabnotes.CollabNotes.dto.NoteDTO;
import com.collabnotes.CollabNotes.dto.UserResponse;
import com.collabnotes.CollabNotes.metrics.MetricsService;
import com.collabnotes.CollabNotes.service.NoteService;
import com.collabnotes.CollabNotes.service.NoteSessionService;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;
import com.collabnotes.CollabNotes.service.UserService;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ExecutionException;

@Controller
public class NoteWebSocketController {

    private static final Logger logger = LoggerFactory.getLogger(NoteWebSocketController.class);

    @Autowired
    private NoteService noteService;

    @Autowired
    private UserService userService;

    @Autowired
    private NoteSessionService sessionService;

    @Autowired
    private SimpMessagingTemplate messagingTemplate;

    @Autowired
    private MetricsService metricsService;

    /**
     * Handle full content updates to a note
     */
    @MessageMapping("/notes/{noteId}/update")
    @org.springframework.messaging.handler.annotation.SendTo("/topic/notes/{noteId}") 
    public NoteContentUpdateMessage updateNote(@DestinationVariable String noteId,
                                               NoteContentUpdateMessage message,
                                               @Header(value = "Authorization", required = false) String token) throws Exception {
        long startTime = System.currentTimeMillis();
        try{
            String firebaseToken = token;
            if (token != null && token.startsWith("Bearer ")) {
                firebaseToken = token.substring(7);
            }

            if (firebaseToken == null || firebaseToken.isEmpty()) {
                logger.error("Authorization token is missing for note update: {}", noteId);
                throw new IllegalArgumentException("Authorization token is required");
            }
            FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(firebaseToken);
            String userId = decodedToken.getUid();
            logger.debug("User {} is updating note {}", userId, noteId);

            NoteDTO noteDTO = new NoteDTO();
            noteDTO.setId(noteId);
            noteDTO.setContent(message.getContent());
            noteDTO.setTitle(message.getTitle());
            noteService.updateNote(noteId, noteDTO, userId);

            message.setUserId(userId);
            message.setTimestamp(Date.from(Instant.now()));
            message.setNoteId(noteId);

            return message;
        }
        finally {
            metricsService.recordOperation("websocket.updateNote.time",
                    System.currentTimeMillis() - startTime);
        }

    }

    /**
     * Handle partial content updates
     */
    @MessageMapping("/notes/{noteId}/partial")
    @org.springframework.messaging.handler.annotation.SendTo("/topic/notes/{noteId}/partial")
    public NotePartialUpdateMessage updateNotePartial(@DestinationVariable String noteId,
                                                      NotePartialUpdateMessage message,
                                                      @Header(value = "Authorization", required = false) String token) throws FirebaseAuthException {

        String firebaseToken = token;
        if (token != null && token.startsWith("Bearer ")) {
            firebaseToken = token.substring(7);
        }

        if (firebaseToken == null || firebaseToken.isEmpty()) {
            logger.error("Authorization token is missing for partial update: {}", noteId);
            throw new IllegalArgumentException("Authorization token is required");
        }

        FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(firebaseToken);
        String userId = decodedToken.getUid();
        logger.debug("User {} is making partial update to note {}", userId, noteId);

        message.setUserId(userId);
        message.setNoteId(noteId);

        return message;
    }

    /**
     * Handle cursor position updates
     */
    @MessageMapping("/notes/{noteId}/cursor")
    @org.springframework.messaging.handler.annotation.SendTo("/topic/notes/{noteId}/cursors")
    public CursorPositionMessage updateCursorPosition(@DestinationVariable String noteId,
                                                      CursorPositionMessage message,
                                                      @Header(value = "Authorization", required = false) String token) throws FirebaseAuthException {

        String firebaseToken = token;
        if (token != null && token.startsWith("Bearer ")) {
            firebaseToken = token.substring(7);
        }

        if (firebaseToken == null || firebaseToken.isEmpty()) {
            logger.error("Authorization token is missing for cursor update: {}", noteId);
            throw new IllegalArgumentException("Authorization token is required");
        }

        FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(firebaseToken);
        String userId = decodedToken.getUid();
        logger.debug("User {} is updating cursor position in note {}", userId, noteId);

        message.setUserId(userId);
        message.setNoteId(noteId);

        return message;
    }

    /**
     * Handle user presence updates (joining/leaving a note)
     */
    @MessageMapping("/notes/{noteId}/presence")
    @org.springframework.messaging.handler.annotation.SendTo("/topic/notes/{noteId}/presence")
    public UserPresenceMessage updatePresence(@DestinationVariable String noteId,
                                              UserPresenceMessage message,
                                              @Header(value = "Authorization", required = false) String token) throws FirebaseAuthException {

        String firebaseToken = token;
        if (token != null && token.startsWith("Bearer ")) {
            firebaseToken = token.substring(7);
        }

        if (firebaseToken == null || firebaseToken.isEmpty()) {
            logger.error("Authorization token is missing for presence update: {}", noteId);
            throw new IllegalArgumentException("Authorization token is required");
        }

        FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(firebaseToken);
        String userId = decodedToken.getUid();
        logger.debug("User {} is updating presence in note {}: {}", userId, noteId, message.isJoining() ? "joining" : "leaving");

        message.setUserId(userId);
        message.setNoteId(noteId);

        if (message.isJoining()) {
            sessionService.addUserToNote(noteId, userId);
            UserResponse user = userService.getUserInfo(userId);
            message.setUserName(user.getEmail());
        } else {
            sessionService.removeUserFromNote(noteId, userId);
        }

        return message;
    }

    /**
     * Handle typing indicator updates
     */
    @MessageMapping("/notes/{noteId}/typing")
    @org.springframework.messaging.handler.annotation.SendTo("/topic/notes/{noteId}/typing")
    public TypingIndicatorMessage updateTypingStatus(@DestinationVariable String noteId,
                                                     TypingIndicatorMessage message,
                                                     @Header(value = "Authorization", required = false) String token) throws FirebaseAuthException {

        String firebaseToken = token;
        if (token != null && token.startsWith("Bearer ")) {
            firebaseToken = token.substring(7);
        }

        if (firebaseToken == null || firebaseToken.isEmpty()) {
            logger.error("Authorization token is missing for typing indicator: {}", noteId);
            throw new IllegalArgumentException("Authorization token is required");
        }

        FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(firebaseToken);
        String userId = decodedToken.getUid();
        logger.debug("User {} is updating typing status in note {}", userId, noteId);

        message.setUserId(userId);
        message.setNoteId(noteId);

        return message;
    }

    /**
     * Handle comments on notes
     */
    @MessageMapping("/notes/{noteId}/comment")
    @org.springframework.messaging.handler.annotation.SendTo("/topic/notes/{noteId}/comments")
    public CommentMessage handleComment(@DestinationVariable String noteId,
                                        CommentMessage message,
                                        @Header(value = "Authorization", required = false) String token) throws ExecutionException, InterruptedException, FirebaseAuthException {

        String firebaseToken = token;
        if (token != null && token.startsWith("Bearer ")) {
            firebaseToken = token.substring(7);
        }

        if (firebaseToken == null || firebaseToken.isEmpty()) {
            logger.error("Authorization token is missing for comment: {}", noteId);
            throw new IllegalArgumentException("Authorization token is required");
        }

        FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(firebaseToken);
        String userId = decodedToken.getUid();
        logger.debug("User {} is commenting on note {}", userId, noteId);

        message.setUserId(userId);
        message.setNoteId(noteId);

        NoteDTO note = noteService.getNoteById(noteId, userId);
        if (note == null) {
            ErrorMessage error = new ErrorMessage();
            error.setErrorCode("PERMISSION_DENIED");
            error.setErrorMessage("You don't have permission to comment on this note");
            error.setOriginalMessageId(message.getMessageId());
            messagingTemplate.convertAndSendToUser(userId,
                    "/queue/errors", error);
            return null;
        }
        return message;
    }

    /**
     * Request initial state when joining a note
     * Sends the full note state to the topic.
     */
    @MessageMapping("/notes/{noteId}/state")
    public void requestNoteState(@DestinationVariable String noteId,
                                 @Header(value = "Authorization", required = false) String token) throws ExecutionException, InterruptedException, FirebaseAuthException {

        String firebaseToken = token;
        if (token != null && token.startsWith("Bearer ")) {
            firebaseToken = token.substring(7);
        }

        if (firebaseToken == null || firebaseToken.isEmpty()) {
            logger.error("Authorization token is missing for state request: {}", noteId);
            return;
        }

        FirebaseToken decodedToken = FirebaseAuth.getInstance().verifyIdToken(firebaseToken);
        String userId = decodedToken.getUid();
        logger.debug("User {} is requesting initial state for note {}", userId, noteId);

        NoteDTO note = noteService.getNoteById(noteId, userId);
        if (note == null) {
            ErrorMessage error = new ErrorMessage();
            error.setErrorCode("PERMISSION_DENIED");
            error.setErrorMessage("You don't have permission to access this note");
            messagingTemplate.convertAndSendToUser(userId,
                    "/queue/errors", error);
            return;
        }

        // Get active users for this note
        Set<String> activeUserIds = sessionService.getUsersViewingNote(noteId);
        logger.info("Active users for note {}: {}", noteId, activeUserIds);

        // Create a map of user details for active users
        Map<String, NoteStateMessage.UserInfo> activeUsers = new HashMap<>();
        for (String activeUserId : activeUserIds) {
            try {
                UserResponse userResponse = userService.getUserInfo(activeUserId);
                NoteStateMessage.UserInfo userInfo = new NoteStateMessage.UserInfo();
                userInfo.setUserId(activeUserId);
                userInfo.setEmail(userResponse.getEmail());
                userInfo.setDisplayName(userResponse.getDisplayName());
                activeUsers.put(activeUserId, userInfo);
            } catch (Exception e) {
                logger.error("Failed to get user info for {}", activeUserId, e);
            }
        }

        // Create state message with current note content and active users
        NoteStateMessage stateMessage = new NoteStateMessage();
        stateMessage.setNoteId(noteId);
        stateMessage.setTitle(note.getTitle());
        stateMessage.setContent(note.getContent());
        stateMessage.setActiveUsers(activeUsers);

        // Get collaborators (users with access to this note)
        List<String> collaboratorIds = noteService.getNoteCollaborators(noteId, userId);
        Map<String, NoteStateMessage.UserInfo> collaborators = new HashMap<>();
        for (String collabId : collaboratorIds) {
            if (activeUsers.containsKey(collabId)) {
                collaborators.put(collabId, activeUsers.get(collabId));
                continue;
            }

            try {
                UserResponse userResponse = userService.getUserInfo(collabId);
                NoteStateMessage.UserInfo userInfo = new NoteStateMessage.UserInfo();
                userInfo.setUserId(collabId);
                userInfo.setEmail(userResponse.getEmail());
                userInfo.setDisplayName(userResponse.getDisplayName());
                collaborators.put(collabId, userInfo);
            } catch (Exception e) {
                logger.error("Failed to get collaborator info for {}", collabId, e);
            }
        }
        stateMessage.setCollaborators(collaborators);

        // Send the state message to the topic
        messagingTemplate.convertAndSend("/topic/notes/" + noteId + "/state", stateMessage);
        logger.debug("Sent initial state for note {} to topic /topic/notes/{}/state", noteId, noteId);

        // Add this user to active users if not already there
        if (!sessionService.isUserViewingNote(noteId, userId)) {
            sessionService.addUserToNote(noteId, userId);

            UserPresenceMessage presenceMessage = new UserPresenceMessage();
            presenceMessage.setJoining(true);
            presenceMessage.setUserId(userId);
            presenceMessage.setNoteId(noteId);

            // Add user details to presence message
            UserResponse user = userService.getUserInfo(userId);
            presenceMessage.setUserName(user.getEmail());
            messagingTemplate.convertAndSend("/topic/notes/" + noteId + "/presence", presenceMessage);
        }
    }
}