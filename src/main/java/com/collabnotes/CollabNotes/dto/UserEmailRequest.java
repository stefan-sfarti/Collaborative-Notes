package com.collabnotes.CollabNotes.dto;

/**
 * DTO for email lookup requests
 */
public class UserEmailRequest {
    private String email;

    // Default constructor for Jackson
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