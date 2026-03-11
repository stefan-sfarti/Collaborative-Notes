package com.collabnotes.collabnotes.websocket.message;

import java.util.Date;

/**
 * Base class for all WebSocket messages
 */
abstract class BaseWebSocketMessage {
    private String messageId;
    private String userId;
    private String noteId;
    private Date timestamp;
    private String messageType;

    public BaseWebSocketMessage() {
        this.timestamp = new Date();
        this.messageId = java.util.UUID.randomUUID().toString();
    }

    public String getMessageId() {
        return messageId;
    }

    public void setMessageId(String messageId) {
        this.messageId = messageId;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public String getNoteId() {
        return noteId;
    }

    public void setNoteId(String noteId) {
        this.noteId = noteId;
    }

    public Date getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Date timestamp) {
        this.timestamp = timestamp;
    }

    public String getMessageType() {
        return messageType;
    }

    public void setMessageType(String messageType) {
        this.messageType = messageType;
    }
}
