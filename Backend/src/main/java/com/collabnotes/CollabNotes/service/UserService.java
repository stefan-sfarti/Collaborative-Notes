package com.collabnotes.CollabNotes.service;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.UserRecord;
import com.collabnotes.CollabNotes.dto.UserResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.collabnotes.CollabNotes.dto.UserResponse;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;

@Service
public class UserService {
    private static final Logger logger = LoggerFactory.getLogger(UserService.class);

    @Autowired
    private  FirebaseAuth firebaseAuth;

    /**
     * Look up a user ID by email using Firebase Auth
     * @param email The email to look up
     * @return User ID if found, null otherwise
     */
    @Cacheable(value = "userEmailCache", key = "#email")
    public String findUserIdByEmail(String email) {
        try {
            UserRecord userRecord = firebaseAuth.getUserByEmail(email);
            return userRecord.getUid();
        } catch (FirebaseAuthException e) {
            logger.error("Error finding user by email: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Get user information by user ID
     * @param userId The Firebase user ID
     * @return UserResponse object with user details
     */
    @Cacheable(value = "userCache", key = "#userId")
    public UserResponse getUserInfo(String userId) {
        try {
            UserRecord userRecord = firebaseAuth.getUser(userId);
            return new UserResponse(
                    userRecord.getUid(),
                    userRecord.getEmail(),
                    userRecord.getDisplayName(),
                    userRecord.getPhotoUrl()
            );
        } catch (FirebaseAuthException e) {
            logger.error("Error getting user info: {}", e.getMessage());
            return null;
        }
    }

    @CacheEvict(value = "userCache", key = "#userId")
    public void refreshUserCache(String userId) {

    }
}