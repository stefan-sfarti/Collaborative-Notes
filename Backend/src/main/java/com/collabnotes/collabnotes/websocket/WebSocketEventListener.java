package com.collabnotes.collabnotes.websocket;

import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.collabnotes.collabnotes.service.NoteSessionService;
import com.collabnotes.collabnotes.websocket.message.UserPresenceMessage;

@Component
public class WebSocketEventListener {
    private static final Logger logger = LoggerFactory.getLogger(WebSocketEventListener.class);
    private final NoteSessionService sessionService;
    private final SimpMessagingTemplate messagingTemplate;

    public WebSocketEventListener(NoteSessionService sessionService, SimpMessagingTemplate messagingTemplate) {
        this.sessionService = sessionService;
        this.messagingTemplate = messagingTemplate;
    }

    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        
        Map<String, Object> attributes = headerAccessor.getSessionAttributes();
        if (attributes == null) return;

        String userId = (String) attributes.get("userId");
        @SuppressWarnings("unchecked")
        Set<String> noteIds = (Set<String>) attributes.get("noteIds");

        if (userId != null && noteIds != null && !noteIds.isEmpty()) {
            logger.info("User {} disconnected violently. Cleaning up sessions.", userId);
            for (String noteId : noteIds) {
                // Remove from session tracking
                sessionService.removeUserFromNote(noteId, userId);
                
                // Broadcast departure
                UserPresenceMessage presenceMessage = new UserPresenceMessage();
                presenceMessage.setUserId(userId);
                presenceMessage.setNoteId(noteId);
                presenceMessage.setJoining(false);
                presenceMessage.setTimestamp(Date.from(Instant.now()));
                
                messagingTemplate.convertAndSend("/topic/notes/" + noteId + "/presence", presenceMessage);
            }
        }
    }
}