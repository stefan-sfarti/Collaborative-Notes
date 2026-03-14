package com.collabnotes.collabnotes.dto;

public class AuthResponse {
    private String token;
    private String userId;
    private String email;
    private String displayName;

    public AuthResponse(String token, String userId, String email, String displayName) {
        this.token = token;
        this.userId = userId;
        this.email = email;
        this.displayName = displayName;
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
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
}
