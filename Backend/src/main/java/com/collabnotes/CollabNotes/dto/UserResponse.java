package com.collabnotes.CollabNotes.dto;

/**
 * DTO for user response data
 */
public class UserResponse {
    private String userId;
    private String email;
    private String displayName;
    private String photoUrl;

    // Default constructor for Jackson
    public UserResponse() {
    }

    // Constructor with minimal fields
    public UserResponse(String userId, String email) {
        this.userId = userId;
        this.email = email;
    }

    // Constructor with all fields
    public UserResponse(String userId, String email, String displayName, String photoUrl) {
        this.userId = userId;
        this.email = email;
        this.displayName = displayName;
        this.photoUrl = photoUrl;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getPhotoUrl() {
        return photoUrl;
    }

    public void setPhotoUrl(String photoUrl) {
        this.photoUrl = photoUrl;
    }
}