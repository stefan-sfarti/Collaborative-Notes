package com.collabnotes.collabnotes.websocket;

import java.time.Instant;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutionException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import com.collabnotes.collabnotes.dto.NoteDTO;
import com.collabnotes.collabnotes.dto.UserResponse;
import com.collabnotes.collabnotes.metrics.MetricsService;
import com.collabnotes.collabnotes.service.NoteService;
import com.collabnotes.collabnotes.service.NoteSessionService;
import com.collabnotes.collabnotes.service.UserService;
import com.collabnotes.collabnotes.util.JwtUtil;
import com.collabnotes.collabnotes.websocket.message.CommentMessage;
import com.collabnotes.collabnotes.websocket.message.CursorPositionMessage;
import com.collabnotes.collabnotes.websocket.message.ErrorMessage;
import com.collabnotes.collabnotes.websocket.message.NoteContentUpdateMessage;
import com.collabnotes.collabnotes.websocket.message.NotePartialUpdateMessage;
import com.collabnotes.collabnotes.websocket.message.NoteStateMessage;
import com.collabnotes.collabnotes.websocket.message.TypingIndicatorMessage;
import com.collabnotes.collabnotes.websocket.message.UserPresenceMessage;

@Controller
public class NoteWebSocketController {

    private static final Logger logger = LoggerFactory.getLogger(NoteWebSocketController.class);

    private final NoteService noteService;
    private final UserService userService;
    private final NoteSessionService sessionService;
    private final SimpMessagingTemplate messagingTemplate;
    private final MetricsService metricsService;
    private final JwtUtil jwtUtil;

    public NoteWebSocketController(NoteService noteService, UserService userService,
            NoteSessionService noteSessionService, SimpMessagingTemplate simpMessagingTemplate,
            MetricsService metricsService, JwtUtil jwtUtil) {
        this.noteService = noteService;
        this.userService = userService;
        this.sessionService = noteSessionService;
        this.messagingTemplate = simpMessagingTemplate;
        this.metricsService = metricsService;
        this.jwtUtil = jwtUtil;
    }

    private void assertHasAccess(String noteId, String userId) {
        if (!noteService.hasNoteAccess(noteId, userId)) {
            logger.warn("User {} attempted unauthorized access to note {}", userId, noteId);
            throw new IllegalArgumentException("Unauthorized to access this note");
        }
    }

    private String resolveUserId(String token, SimpMessageHeaderAccessor headerAccessor) {
        String userId = jwtUtil.extractUserId(token);
        if (userId != null) {
            return userId;
        }

        if (headerAccessor != null && headerAccessor.getSessionAttributes() != null) {
            Object sessionUserId = headerAccessor.getSessionAttributes().get("userId");
            if (sessionUserId instanceof String sessionUserIdStr && !sessionUserIdStr.isBlank()) {
                return sessionUserIdStr;
            }
        }

        return null;
    }

    @MessageMapping("/notes/{noteId}/update")
    @SendTo("/topic/notes/{noteId}")
    public NoteContentUpdateMessage updateNote(@DestinationVariable String noteId,
            NoteContentUpdateMessage message,
            @Header(value = "Authorization", required = false) String token,
            SimpMessageHeaderAccessor headerAccessor) throws Exception {
        long startTime = System.currentTimeMillis();
        try {
            String userId = resolveUserId(token, headerAccessor);
            if (userId == null) {
                logger.error("Authorization token is missing or invalid for note update: {}", noteId);
                throw new IllegalArgumentException("Authorization token is required");
            }

            assertHasAccess(noteId, userId);

            logger.debug("User {} is updating note {}", userId, noteId);

            NoteDTO noteDTO = new NoteDTO();
            noteDTO.setId(noteId);
            noteDTO.setContent(message.getContent());
            noteDTO.setTitle(message.getTitle());
            NoteDTO updatedNote = noteService.updateNote(noteId, noteDTO, userId);
            
            if (updatedNote == null) {
                logger.error("Failed to update note in DB: noteId={}", noteId);
                throw new IllegalStateException("Failed to save note contents");
            }

            message.setUserId(userId);
            message.setTimestamp(Date.from(Instant.now()));
            message.setNoteId(noteId);

            return message;
        } finally {
            metricsService.recordOperation("websocket.updateNote.time",
                    System.currentTimeMillis() - startTime);
        }
    }

    @MessageMapping("/notes/{noteId}/partial")
    @SendTo("/topic/notes/{noteId}/partial")
    public NotePartialUpdateMessage updateNotePartial(@DestinationVariable String noteId,
            NotePartialUpdateMessage message,
            @Header(value = "Authorization", required = false) String token,
            SimpMessageHeaderAccessor headerAccessor) {

        String userId = resolveUserId(token, headerAccessor);
        if (userId == null) {
            logger.error("Authorization token is missing or invalid for partial update: {}", noteId);
            throw new IllegalArgumentException("Authorization token is required");
        }

        assertHasAccess(noteId, userId);

        logger.debug("User {} is making partial update to note {}", userId, noteId);

        message.setUserId(userId);
        message.setNoteId(noteId);

        return message;
    }

    @MessageMapping("/notes/{noteId}/cursor")
    @SendTo("/topic/notes/{noteId}/cursors")
    public CursorPositionMessage updateCursorPosition(@DestinationVariable String noteId,
            CursorPositionMessage message,
            @Header(value = "Authorization", required = false) String token,
            SimpMessageHeaderAccessor headerAccessor) {

        String userId = resolveUserId(token, headerAccessor);
        if (userId == null) {
            logger.error("Authorization token is missing or invalid for cursor update: {}", noteId);
            throw new IllegalArgumentException("Authorization token is required");
        }

        assertHasAccess(noteId, userId);

        logger.debug("User {} is updating cursor position in note {}", userId, noteId);

        message.setUserId(userId);
        message.setNoteId(noteId);

        return message;
    }

    @MessageMapping("/notes/{noteId}/presence")
    @SendTo("/topic/notes/{noteId}/presence")
    public UserPresenceMessage updatePresence(@DestinationVariable String noteId,
            UserPresenceMessage message,
            @Header(value = "Authorization", required = false) String token,
            SimpMessageHeaderAccessor headerAccessor) {

        String userId = resolveUserId(token, headerAccessor);
        if (userId == null) {
            logger.error("Authorization token is missing or invalid for presence update: {}", noteId);
            throw new IllegalArgumentException("Authorization token is required");
        }

        assertHasAccess(noteId, userId);

        logger.debug("User {} is updating presence in note {}: {}", userId, noteId,
                message.isJoining() ? "joining" : "leaving");

        message.setUserId(userId);
        message.setNoteId(noteId);

        if (message.isJoining()) {
            sessionService.addUserToNote(noteId, userId);
            UserResponse user = userService.getUserInfo(userId);
            message.setUserName(user != null ? user.getEmail() : "Unknown");
        } else {
            sessionService.removeUserFromNote(noteId, userId);
        }

        return message;
    }

    @MessageMapping("/notes/{noteId}/typing")
    @SendTo("/topic/notes/{noteId}/typing")
    public TypingIndicatorMessage updateTypingStatus(@DestinationVariable String noteId,
            TypingIndicatorMessage message,
            @Header(value = "Authorization", required = false) String token,
            SimpMessageHeaderAccessor headerAccessor) {

        String userId = resolveUserId(token, headerAccessor);
        if (userId == null) {
            logger.error("Authorization token is missing or invalid for typing indicator: {}", noteId);
            throw new IllegalArgumentException("Authorization token is required");
        }

        assertHasAccess(noteId, userId);

        logger.debug("User {} is updating typing status in note {}", userId, noteId);

        message.setUserId(userId);
        message.setNoteId(noteId);

        return message;
    }

    @MessageMapping("/notes/{noteId}/comment")
    @SendTo("/topic/notes/{noteId}/comments")
    public CommentMessage handleComment(@DestinationVariable String noteId,
            CommentMessage message,
            @Header(value = "Authorization", required = false) String token,
            SimpMessageHeaderAccessor headerAccessor)
            throws ExecutionException, InterruptedException {

        String userId = resolveUserId(token, headerAccessor);
        if (userId == null) {
            logger.error("Authorization token is missing or invalid for comment: {}", noteId);
            throw new IllegalArgumentException("Authorization token is required");
        }

        assertHasAccess(noteId, userId);

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

    @MessageMapping("/notes/{noteId}/state")
    public void requestNoteState(@DestinationVariable String noteId,
            @Header(value = "Authorization", required = false) String token,
            SimpMessageHeaderAccessor headerAccessor) {

        String userId = resolveUserId(token, headerAccessor);
        if (userId == null) {
            logger.error("Authorization token is missing or invalid for state request: {}", noteId);
            return;
        }

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

        // Ensure requester is registered in-session before building state payload.
        if (!sessionService.isUserViewingNote(noteId, userId)) {
            sessionService.addUserToNote(noteId, userId);
        }

        Set<String> activeUserIds = sessionService.getUsersViewingNote(noteId);
        logger.info("Active users for note {}: {}", noteId, activeUserIds);

        Map<String, NoteStateMessage.UserInfo> activeUsers = new HashMap<>();
        for (String activeUserId : activeUserIds) {
            try {
                UserResponse userResponse = userService.getUserInfo(activeUserId);
                NoteStateMessage.UserInfo userInfo = new NoteStateMessage.UserInfo();
                userInfo.setUserId(activeUserId);
                userInfo.setEmail(userResponse != null ? userResponse.getEmail() : null);
                userInfo.setDisplayName(userResponse != null ? userResponse.getDisplayName() : null);
                activeUsers.put(activeUserId, userInfo);
            } catch (Exception e) {
                logger.error("Failed to get user info for {}", activeUserId, e);
            }
        }

        NoteStateMessage stateMessage = new NoteStateMessage();
        stateMessage.setNoteId(noteId);
        stateMessage.setTitle(note.getTitle());
        stateMessage.setContent(note.getContent());
        stateMessage.setActiveUsers(activeUsers);

        List<String> collaboratorIds = new java.util.ArrayList<>(noteService.getNoteCollaborators(noteId, userId));
        if (!collaboratorIds.contains(note.getOwnerId())) {
            collaboratorIds.add(note.getOwnerId());
        }
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
                userInfo.setEmail(userResponse != null ? userResponse.getEmail() : null);
                userInfo.setDisplayName(userResponse != null ? userResponse.getDisplayName() : null);
                collaborators.put(collabId, userInfo);
            } catch (Exception e) {
                logger.error("Failed to get collaborator info for {}", collabId, e);
            }
        }
        stateMessage.setCollaborators(collaborators);

            messagingTemplate.convertAndSendToUser(userId,
                    "/queue/notes/" + noteId + "/state", stateMessage);
            logger.debug("Sent initial state for note {} to user queue /user/queue/notes/{}/state for user {}",
                    noteId, noteId, userId);

        UserPresenceMessage presenceMessage = new UserPresenceMessage();
        presenceMessage.setJoining(true);
        presenceMessage.setUserId(userId);
        presenceMessage.setNoteId(noteId);

        UserResponse user = userService.getUserInfo(userId);
        presenceMessage.setUserName(user != null ? user.getEmail() : "Unknown");
        messagingTemplate.convertAndSend("/topic/notes/" + noteId + "/presence", presenceMessage);
    }
}
