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
import com.collabnotes.collabnotes.service.ot.OTAuthorityService;
import com.collabnotes.collabnotes.service.ot.OTAuthorityService.Accepted;
import com.collabnotes.collabnotes.service.ot.OTAuthorityService.CatchUp;
import com.collabnotes.collabnotes.util.JwtUtil;
import com.collabnotes.collabnotes.websocket.message.CommentMessage;
import com.collabnotes.collabnotes.websocket.message.CursorPositionMessage;
import com.collabnotes.collabnotes.websocket.message.ErrorMessage;
import com.collabnotes.collabnotes.websocket.message.NoteContentUpdateMessage;
import com.collabnotes.collabnotes.websocket.message.NotePartialUpdateMessage;
import com.collabnotes.collabnotes.websocket.message.NoteStateMessage;
import com.collabnotes.collabnotes.websocket.message.OTCatchUpMessage;
import com.collabnotes.collabnotes.websocket.message.OTStepsBroadcastMessage;
import com.collabnotes.collabnotes.websocket.message.OTSubmitStepsMessage;
import com.collabnotes.collabnotes.websocket.message.TypingIndicatorMessage;
import com.collabnotes.collabnotes.websocket.message.UserPresenceMessage;

@Controller
public class NoteWebSocketController {

    private static final Logger logger = LoggerFactory.getLogger(NoteWebSocketController.class);
    private static final String USER_ERRORS_QUEUE = "/queue/errors";
    private static final String USER_NOTE_QUEUE_PREFIX = "/queue/notes/";

    private final NoteService noteService;
    private final UserService userService;
    private final NoteSessionService sessionService;
    private final SimpMessagingTemplate messagingTemplate;
    private final MetricsService metricsService;
    private final JwtUtil jwtUtil;
    private final OTAuthorityService otAuthorityService;

    public NoteWebSocketController(NoteService noteService, UserService userService,
            NoteSessionService noteSessionService, SimpMessagingTemplate simpMessagingTemplate,
            MetricsService metricsService, JwtUtil jwtUtil, OTAuthorityService otAuthorityService) {
        this.noteService = noteService;
        this.userService = userService;
        this.sessionService = noteSessionService;
        this.messagingTemplate = simpMessagingTemplate;
        this.metricsService = metricsService;
        this.jwtUtil = jwtUtil;
        this.otAuthorityService = otAuthorityService;
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

    /**
     * Broadcast-only update endpoint. DB persistence is handled by the REST
     * PUT /api/notes/{id} path; this endpoint just relays the message to all
     * subscribers so editors can update in real-time.
     */
    @MessageMapping("/notes/{noteId}/update")
    @SendTo("/topic/notes/{noteId}")
    public NoteContentUpdateMessage updateNote(@DestinationVariable String noteId,
            NoteContentUpdateMessage message,
            @Header(value = "Authorization", required = false) String token,
            SimpMessageHeaderAccessor headerAccessor) {
        long startTime = System.currentTimeMillis();
        try {
            String userId = resolveUserId(token, headerAccessor);
            if (userId == null) {
                logger.error("Authorization token is missing or invalid for note update: {}", noteId);
                throw new IllegalArgumentException("Authorization token is required");
            }

            assertHasAccess(noteId, userId);

            logger.debug("User {} is broadcasting update for note {}", userId, noteId);

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
            messagingTemplate.convertAndSendToUser(userId, USER_ERRORS_QUEUE, error);
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
            messagingTemplate.convertAndSendToUser(userId, USER_ERRORS_QUEUE, error);
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
        stateMessage.setVersionNumber(note.getVersion() != null ? note.getVersion() : 0);
        stateMessage.setOtVersion(otAuthorityService.getVersion(noteId));
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
                USER_NOTE_QUEUE_PREFIX + noteId + "/state", stateMessage);
        logger.debug("Sent initial state for note {} to user {} (otVersion={})",
                noteId, userId, stateMessage.getOtVersion());

        UserPresenceMessage presenceMessage = new UserPresenceMessage();
        presenceMessage.setJoining(true);
        presenceMessage.setUserId(userId);
        presenceMessage.setNoteId(noteId);

        UserResponse user = userService.getUserInfo(userId);
        presenceMessage.setUserName(user != null ? user.getEmail() : "Unknown");
        messagingTemplate.convertAndSend("/topic/notes/" + noteId + "/presence", presenceMessage);
    }

    /**
     * OT step submission endpoint (prosemirror-collab protocol).
     *
     * On accept: broadcasts the new steps to all note subscribers so every
     * client can advance their local document via {@code receiveTransaction}.
     *
     * On catch-up: sends the missing steps only to the submitting user so the
     * client can rebase its pending steps and retry.
     */
    @MessageMapping("/notes/{noteId}/ot-submit")
    public void submitOTSteps(@DestinationVariable String noteId,
            OTSubmitStepsMessage message,
            @Header(value = "Authorization", required = false) String token,
            SimpMessageHeaderAccessor headerAccessor) {

        String userId = resolveUserId(token, headerAccessor);
        if (userId == null) {
            logger.warn("OT submit rejected — missing auth for note {}", noteId);
            return;
        }

        assertHasAccess(noteId, userId);

        // Always use the server-resolved userId, never trust the client body.
        var result = otAuthorityService.submitSteps(
                noteId, message.getVersion(), message.getSteps(), userId);

        switch (result) {
            case Accepted(int newVersion, var steps, var ignoredClientId) -> {
                OTStepsBroadcastMessage broadcast = new OTStepsBroadcastMessage();
                broadcast.setVersion(newVersion);
                broadcast.setSteps(steps);
                broadcast.setClientId(userId);
                messagingTemplate.convertAndSend("/topic/notes/" + noteId + "/ot", broadcast);
                logger.debug("OT accepted {} step(s) for note {} → version {}",
                        steps.size(), noteId, newVersion);
            }
            case CatchUp(int serverVersion, var missing) -> {
                OTCatchUpMessage catchUpMsg = new OTCatchUpMessage();
                catchUpMsg.setVersion(serverVersion);
                catchUpMsg.setSteps(missing.stream()
                        .map(e -> new OTCatchUpMessage.StepWithClient(e.step(), e.clientId()))
                        .toList());
                messagingTemplate.convertAndSendToUser(
                        userId, USER_NOTE_QUEUE_PREFIX + noteId + "/ot-catchup", catchUpMsg);
                logger.debug("OT catch-up sent to user {} for note {} ({} missing steps)",
                        userId, noteId, missing.size());
            }
            default -> logger.warn("OT submit error for note {} user {}: {}", noteId, userId, result);
        }
    }

    /**
     * OT resync endpoint: client requests full step history since a given version.
     *
     * Used when reconnecting or when the catch-up path returns no steps (server
     * restarted and lost in-memory history). An empty step list in the response
     * signals the client to re-bootstrap from the REST snapshot.
     */
    @MessageMapping("/notes/{noteId}/ot-resync")
    public void resyncOTSteps(@DestinationVariable String noteId,
            OTSubmitStepsMessage message,
            @Header(value = "Authorization", required = false) String token,
            SimpMessageHeaderAccessor headerAccessor) {

        String userId = resolveUserId(token, headerAccessor);
        if (userId == null) return;

        assertHasAccess(noteId, userId);

        var missing = otAuthorityService.stepsSince(noteId, message.getVersion());
        OTCatchUpMessage catchUpMsg = new OTCatchUpMessage();
        catchUpMsg.setVersion(otAuthorityService.getVersion(noteId));
        catchUpMsg.setSteps(missing.stream()
                .map(e -> new OTCatchUpMessage.StepWithClient(e.step(), e.clientId()))
                .toList());
        messagingTemplate.convertAndSendToUser(
                userId, USER_NOTE_QUEUE_PREFIX + noteId + "/ot-catchup", catchUpMsg);
    }
}
