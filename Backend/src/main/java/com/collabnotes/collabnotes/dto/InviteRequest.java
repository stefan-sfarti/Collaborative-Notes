package com.collabnotes.collabnotes.dto;

public class InviteRequest {
    private String email;

    public InviteRequest() {}

    public InviteRequest(String email) {
        this.email = email;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }
}
