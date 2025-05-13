package com.collabnotes.CollabNotes.dto;

import java.util.Date;
import java.util.List;
import java.util.Map;

public class NoteDTO {
    private String id;
    private String title;
    private String content;
    private String ownerId;
    private List<String> collaboratorIds;
    private Date createdAt;
    private Date updatedAt;
    private Map<String, Object> analysis;

    // Default constructor
    public NoteDTO() {}

    // Constructor with all fields
    public NoteDTO(String id, String title, String content, String ownerId,
                   List<String> collaboratorIds, Date createdAt, Date updatedAt) {
        this.id = id;
        this.title = title;
        this.content = content;
        this.ownerId = ownerId;
        this.collaboratorIds = collaboratorIds;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    // Getters and setters
    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public List<String> getCollaboratorIds() {
        return collaboratorIds;
    }

    public void setCollaboratorIds(List<String> collaboratorIds) {
        this.collaboratorIds = collaboratorIds;
    }

    public Date getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt) {
        this.createdAt = createdAt;
    }

    public Date getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt) {
        this.updatedAt = updatedAt;
    }

    public Map<String, Object> getAnalysis() {
        return analysis;
    }

    public void setAnalysis(Map<String, Object> analysis) {
        this.analysis = analysis;
    }
}