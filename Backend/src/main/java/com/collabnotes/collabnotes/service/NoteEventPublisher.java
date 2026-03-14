package com.collabnotes.collabnotes.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

@Service
public class NoteEventPublisher {

    private static final Logger logger = LoggerFactory.getLogger(NoteEventPublisher.class);
    private static final String TOPIC = "note-updates";

    private final RedisTemplate<String, Object> redisTemplate;

    public NoteEventPublisher(RedisTemplate<String, Object> redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public void publishNoteUpdate(String noteId, String userId, String action) {
        Map<String, Object> message = new HashMap<>();
        message.put("noteId", noteId);
        message.put("userId", userId);
        message.put("action", action);
        message.put("timestamp", Instant.now().toEpochMilli());

        try {
            redisTemplate.convertAndSend(TOPIC, message);
            logger.info("Published note update: noteId={}, userId={}, action={}", noteId, userId, action);
        } catch (Exception e) {
            logger.error("Failed to publish note update: {}", e.getMessage());
        }
    }
}
