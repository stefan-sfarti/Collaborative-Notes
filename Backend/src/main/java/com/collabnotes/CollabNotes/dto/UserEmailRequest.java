package com.collabnotes.CollabNotes.dto;

/**
 * DTO for email lookup requests
 */
public class UserEmailRequest {
    private String email;

    public UserEmailRequest() {
    }

    public UserEmailRequest(String email) {
        this.email = email;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}