package com.collabnotes.collabnotes.websocket.message;

import java.util.Map;

/**
 * Message for initial note state synchronization
 */
public class NoteStateMessage extends BaseWebSocketMessage {
    private String noteId;
    private String title;
    private String content;
    private long versionNumber;
    private Map<String, UserInfo> activeUsers;
    private Map<String, UserInfo> collaborators;

    public NoteStateMessage() {
        super();
        setMessageType("NOTE_STATE");
    }

    public String getNoteId() {
        return noteId;
    }

    public void setNoteId(String noteId) {
        this.noteId = noteId;
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

    public long getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(long versionNumber) {
        this.versionNumber = versionNumber;
    }

    public Map<String, UserInfo> getActiveUsers() {
        return activeUsers;
    }

    public void setActiveUsers(Map<String, UserInfo> activeUsers) {
        this.activeUsers = activeUsers;
    }

    public Map<String, UserInfo> getCollaborators() {
        return collaborators;
    }

    public void setCollaborators(Map<String, UserInfo> collaborators) {
        this.collaborators = collaborators;
    }

    public static class UserInfo {
        private String userId;
        private String userName;
        private String userColor;
        private String email;
        private String displayName;
        private boolean isTyping;

        public String getUserId() {
            return userId;
        }

        public void setUserId(String userId) {
            this.userId = userId;
        }

        public String getUserName() {
            return userName;
        }

        public void setUserName(String userName) {
            this.userName = userName;
        }

        public String getUserColor() {
            return userColor;
        }

        public void setUserColor(String userColor) {
            this.userColor = userColor;
        }

        public boolean isTyping() {
            return isTyping;
        }

        public void setTyping(boolean typing) {
            isTyping = typing;
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
}
