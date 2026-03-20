package com.collabnotes.collabnotes.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import com.collabnotes.collabnotes.service.NoteService;
import com.collabnotes.collabnotes.util.JwtUtil;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtUtil jwtUtil;
    private final NoteService noteService;

    public WebSocketConfig(JwtUtil jwtUtil, @Lazy NoteService noteService) {
        this.jwtUtil = jwtUtil;
        this.noteService = noteService;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.setApplicationDestinationPrefixes("/app");
        config.enableSimpleBroker("/topic", "/queue");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-notes")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
                if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String token = accessor.getFirstNativeHeader("Authorization");
                    if (token != null && token.startsWith("Bearer ")) {
                        token = token.substring(7);
                    }
                    String userId = jwtUtil.extractUserId(token);
                    if (userId != null && accessor.getSessionAttributes() != null) {
                        final String principalName = userId;
                        accessor.getSessionAttributes().put("userId", userId);
                        accessor.setUser(() -> principalName);
                    }
                }

                if (accessor != null && StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    String destination = accessor.getDestination();
                    if (destination != null && destination.startsWith("/topic/notes/")) {
                        String[] parts = destination.split("/");
                        if (parts.length >= 4) {
                            String noteId = parts[3];
                            String token = accessor.getFirstNativeHeader("Authorization");
                            if (token != null && token.startsWith("Bearer ")) {
                                token = token.substring(7);
                            }
                            if (token == null) {
                                throw new IllegalArgumentException("Missing or invalid authorization for subscription");
                            }
                            String userId = jwtUtil.extractUserId(token);
                            if (userId == null || !noteService.hasNoteAccess(noteId, userId)) {
                                throw new IllegalArgumentException("Unauthorized to subscribe to this note");
                            }
                            if (accessor.getSessionAttributes() != null) {
                                final String principalName = userId;
                                accessor.getSessionAttributes().put("userId", userId);
                                accessor.setUser(() -> principalName);
                                @SuppressWarnings("unchecked")
                                java.util.Set<String> noteIds = (java.util.Set<String>) accessor.getSessionAttributes().get("noteIds");
                                if (noteIds == null) {
                                    noteIds = new java.util.HashSet<>();
                                    accessor.getSessionAttributes().put("noteIds", noteIds);
                                }
                                noteIds.add(noteId);
                            }
                        }
                    }
                }

                if (accessor != null && StompCommand.SEND.equals(accessor.getCommand())) {
                    String destination = accessor.getDestination();
                    if (destination != null && destination.startsWith("/app/notes/")) {
                        String[] parts = destination.split("/");
                        if (parts.length >= 4) {
                            String noteId = parts[3];
                            String token = accessor.getFirstNativeHeader("Authorization");
                            if (token != null && token.startsWith("Bearer ")) {
                                token = token.substring(7);
                            }

                            String userId = jwtUtil.extractUserId(token);
                            if (userId == null && accessor.getSessionAttributes() != null) {
                                Object sessionUserId = accessor.getSessionAttributes().get("userId");
                                if (sessionUserId instanceof String sessionUserIdStr && !sessionUserIdStr.isBlank()) {
                                    userId = sessionUserIdStr;
                                }
                            }

                            if (userId == null || !noteService.hasNoteAccess(noteId, userId)) {
                                throw new IllegalArgumentException("Unauthorized to send updates to this note");
                            }

                            if (accessor.getSessionAttributes() != null) {
                                final String principalName = userId;
                                accessor.getSessionAttributes().put("userId", userId);
                                accessor.setUser(() -> principalName);
                            }
                        }
                    }
                }
                return message;
            }
        });
    }
}