package com.collabnotes.CollabNotes.service;

import com.collabnotes.CollabNotes.metrics.MetricsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
public class NoteSessionService {
    private static final String NOTE_USERS_PREFIX = "note:users:";
    private static final String USER_ACTIVITY_PREFIX = "user:activity:";

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private MetricsService metricsService;

    /**
     * Add a user to the active session for a note
     */
    public void addUserToNote(String noteId, String userId) {
        long startTime = System.currentTimeMillis();

        String noteKey = NOTE_USERS_PREFIX + noteId;
        redisTemplate.opsForSet().add(noteKey, userId);
        redisTemplate.expire(noteKey, 24, TimeUnit.HOURS);

        updateUserActivity(noteId, userId);

        // Record metrics after user is added
        int activeUsers = getActiveUserCount(noteId);
        metricsService.recordUserActivity(noteId, activeUsers);
        metricsService.recordOperation("session.addUserToNote",
                System.currentTimeMillis() - startTime);
        metricsService.incrementCounter("session.userJoined");
    }

    /**
     * Remove a user from the active session for a note
     */
    public void removeUserFromNote(String noteId, String userId) {
        long startTime = System.currentTimeMillis();

        String noteKey = NOTE_USERS_PREFIX + noteId;
        redisTemplate.opsForSet().remove(noteKey, userId);
        String activityKey = USER_ACTIVITY_PREFIX + noteId;
        redisTemplate.opsForHash().delete(activityKey, userId);

        // Record metrics after user is removed
        int activeUsers = getActiveUserCount(noteId);
        metricsService.recordUserActivity(noteId, activeUsers);
        metricsService.recordOperation("session.removeUserFromNote",
                System.currentTimeMillis() - startTime);
        metricsService.incrementCounter("session.userLeft");
    }

    /**
     * Update user activity timestamp
     */
    public void updateUserActivity(String noteId, String userId) {
        String activityKey = USER_ACTIVITY_PREFIX + noteId;
        redisTemplate.opsForHash().put(activityKey, userId, System.currentTimeMillis());
        redisTemplate.expire(activityKey, 24, TimeUnit.HOURS);
    }

    /**
     * Get last activity timestamp for a user on a note
     * @return timestamp in milliseconds or 0 if not found
     */
    public long getLastActivity(String noteId, String userId) {
        String activityKey = USER_ACTIVITY_PREFIX + noteId;
        Object timestamp = redisTemplate.opsForHash().get(activityKey, userId);
        return timestamp != null ? Long.parseLong(timestamp.toString()) : 0;
    }

    /**
     * Get all users viewing a specific note
     */
    public Set<String> getUsersViewingNote(String noteId) {
        String noteKey = NOTE_USERS_PREFIX + noteId;
        Set<Object> members = redisTemplate.opsForSet().members(noteKey);
        if (members == null) {
            return Collections.emptySet();
        }

        // Convert from Set<Object> to Set<String>
        Set<String> result = new java.util.HashSet<>();
        for (Object member : members) {
            result.add(member.toString());
        }
        return result;
    }

    /**
     * Check if a user is viewing a specific note
     */
    public boolean isUserViewingNote(String noteId, String userId) {
        String noteKey = NOTE_USERS_PREFIX + noteId;
        Boolean isMember = redisTemplate.opsForSet().isMember(noteKey, userId);
        return Boolean.TRUE.equals(isMember);
    }

    /**
     * Get count of active users for a note
     */
    public int getActiveUserCount(String noteId) {
        String noteKey = NOTE_USERS_PREFIX + noteId;
        Long size = redisTemplate.opsForSet().size(noteKey);
        return size != null ? size.intValue() : 0;
    }

    /**
     * Clean up inactive users (users who haven't sent activity for a specified time)
     * @param inactiveThresholdMs time in milliseconds after which a user is considered inactive
     */
    public void cleanupInactiveUsers(long inactiveThresholdMs) {
        long currentTime = System.currentTimeMillis();

        // Get all note activity keys (scan would be more efficient in production)
        Set<String> activityKeys = redisTemplate.keys(USER_ACTIVITY_PREFIX + "*");
        if (activityKeys == null) return;

        for (String activityKey : activityKeys) {
            String noteId = activityKey.substring(USER_ACTIVITY_PREFIX.length());
            Map<Object, Object> userTimestamps = redisTemplate.opsForHash().entries(activityKey);

            for (Map.Entry<Object, Object> entry : userTimestamps.entrySet()) {
                String userId = entry.getKey().toString();
                long timestamp = Long.parseLong(entry.getValue().toString());

                if (currentTime - timestamp > inactiveThresholdMs) {
                    // Remove inactive user
                    removeUserFromNote(noteId, userId);
                }
            }
        }
    }


}