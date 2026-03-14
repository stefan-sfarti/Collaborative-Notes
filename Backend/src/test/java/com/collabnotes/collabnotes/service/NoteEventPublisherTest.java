package com.collabnotes.collabnotes.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;

@ExtendWith(MockitoExtension.class)
class NoteEventPublisherTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    private NoteEventPublisher noteEventPublisher;

    @BeforeEach
    void setUp() {
        noteEventPublisher = new NoteEventPublisher(redisTemplate);
    }

    @Test
    void publishNoteUpdate_whenSuccessful_publishesMessageWithExpectedPayload() {
        noteEventPublisher.publishNoteUpdate("note-1", "user-1", "update");

        ArgumentCaptor<Object> payloadCaptor = ArgumentCaptor.forClass(Object.class);
        verify(redisTemplate).convertAndSend(eq("note-updates"), payloadCaptor.capture());

        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) payloadCaptor.getValue();
        assertEquals("note-1", payload.get("noteId"));
        assertEquals("user-1", payload.get("userId"));
        assertEquals("update", payload.get("action"));
        assertNotNull(payload.get("timestamp"));
    }

    @Test
    void publishNoteUpdate_whenRedisThrows_doesNotPropagateException() {
        doThrow(new RuntimeException("Redis down"))
                .when(redisTemplate)
                .convertAndSend(eq("note-updates"), org.mockito.ArgumentMatchers.any());

        assertDoesNotThrow(() -> noteEventPublisher.publishNoteUpdate("note-1", "user-1", "update"));
    }
}
