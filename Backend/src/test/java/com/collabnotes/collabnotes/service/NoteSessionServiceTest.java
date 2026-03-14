package com.collabnotes.collabnotes.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;

import com.collabnotes.collabnotes.metrics.MetricsService;

@ExtendWith(MockitoExtension.class)
class NoteSessionServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private SetOperations<String, Object> setOperations;

    @Mock
    private HashOperations<String, Object, Object> hashOperations;

    @Mock
    private MetricsService metricsService;

    private NoteSessionService noteSessionService;

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
        lenient().when(redisTemplate.opsForHash()).thenReturn(hashOperations);
        noteSessionService = new NoteSessionService(redisTemplate, metricsService);
    }

    @Test
    void addUserToNote_addsMembershipUpdatesActivityAndMetrics() {
        when(setOperations.size("note:users:note-1")).thenReturn(2L);

        noteSessionService.addUserToNote("note-1", "user-1");

        verify(setOperations).add("note:users:note-1", "user-1");
        verify(redisTemplate).expire("note:users:note-1", 24, TimeUnit.HOURS);
        verify(hashOperations).put(eq("user:activity:note-1"), eq("user-1"), org.mockito.ArgumentMatchers.anyLong());
        verify(redisTemplate).expire("user:activity:note-1", 24, TimeUnit.HOURS);
        verify(metricsService).recordUserActivity("note-1", 2);
        verify(metricsService).recordOperation(eq("session.addUserToNote"), anyLong());
        verify(metricsService).incrementCounter("session.userJoined");
    }

    @Test
    void removeUserFromNote_removesMembershipAndRecordsMetrics() {
        when(setOperations.size("note:users:note-1")).thenReturn(1L);

        noteSessionService.removeUserFromNote("note-1", "user-1");

        verify(setOperations).remove("note:users:note-1", "user-1");
        verify(hashOperations).delete("user:activity:note-1", "user-1");
        verify(metricsService).recordUserActivity("note-1", 1);
        verify(metricsService).recordOperation(eq("session.removeUserFromNote"), anyLong());
        verify(metricsService).incrementCounter("session.userLeft");
    }

    @Test
    void getLastActivity_whenPresent_returnsTimestamp() {
        when(hashOperations.get("user:activity:note-1", "user-1")).thenReturn("12345");

        long result = noteSessionService.getLastActivity("note-1", "user-1");

        assertEquals(12345L, result);
    }

    @Test
    void getLastActivity_whenMissing_returnsZero() {
        when(hashOperations.get("user:activity:note-1", "user-1")).thenReturn(null);

        long result = noteSessionService.getLastActivity("note-1", "user-1");

        assertEquals(0L, result);
    }

    @Test
    void getUsersViewingNote_whenMembersExist_returnsStringSet() {
        when(setOperations.members("note:users:note-1")).thenReturn(Set.of("user-1", "user-2"));

        Set<String> users = noteSessionService.getUsersViewingNote("note-1");

        assertEquals(Set.of("user-1", "user-2"), users);
    }

    @Test
    void getUsersViewingNote_whenNoMembers_returnsEmptySet() {
        when(setOperations.members("note:users:note-1")).thenReturn(null);

        Set<String> users = noteSessionService.getUsersViewingNote("note-1");

        assertTrue(users.isEmpty());
    }

    @Test
    void isUserViewingNote_returnsTrueOnlyForTrueValue() {
        when(setOperations.isMember("note:users:note-1", "user-1")).thenReturn(true);
        when(setOperations.isMember("note:users:note-1", "user-2")).thenReturn(false);

        assertTrue(noteSessionService.isUserViewingNote("note-1", "user-1"));
        assertFalse(noteSessionService.isUserViewingNote("note-1", "user-2"));
    }

    @Test
    void getActiveUserCount_handlesNullSize() {
        when(setOperations.size("note:users:note-1")).thenReturn(null);

        assertEquals(0, noteSessionService.getActiveUserCount("note-1"));
    }

    @Test
    void cleanupInactiveUsers_removesOnlyExpiredUsers() {
        String activityKey = "user:activity:note-1";
        when(redisTemplate.keys("user:activity:*")).thenReturn(Set.of(activityKey));

        Map<Object, Object> entries = new HashMap<>();
        entries.put("stale-user", 1L);
        entries.put("active-user", System.currentTimeMillis());
        when(hashOperations.entries(activityKey)).thenReturn(entries);
        when(setOperations.size(anyString())).thenReturn(0L);

        noteSessionService.cleanupInactiveUsers(1000L);

        verify(setOperations).remove("note:users:note-1", "stale-user");
        verify(hashOperations).delete("user:activity:note-1", "stale-user");
    }
}
