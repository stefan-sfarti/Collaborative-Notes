package com.collabnotes.CollabNotes.websocket;

import java.util.Date;
import java.util.List;
import java.util.ArrayList;
import java.util.Map;

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

/**
 * Message for note content updates
 */
public class NoteContentUpdateMessage extends BaseWebSocketMessage {
    private String title;
    private String content;
    private long versionNumber;

    public NoteContentUpdateMessage() {
        super();
        setMessageType("CONTENT_UPDATE");
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
}

/**
 * Message for partial note updates
 */
class NotePartialUpdateMessage extends BaseWebSocketMessage {
    private int position;
    private int deleteCount;
    private String insertText;
    private long versionNumber;

    public NotePartialUpdateMessage() {
        super();
        setMessageType("PARTIAL_UPDATE");
    }

    public int getPosition() {
        return position;
    }

    public void setPosition(int position) {
        this.position = position;
    }

    public int getDeleteCount() {
        return deleteCount;
    }

    public void setDeleteCount(int deleteCount) {
        this.deleteCount = deleteCount;
    }

    public String getInsertText() {
        return insertText;
    }

    public void setInsertText(String insertText) {
        this.insertText = insertText;
    }

    public long getVersionNumber() {
        return versionNumber;
    }

    public void setVersionNumber(long versionNumber) {
        this.versionNumber = versionNumber;
    }
}

/**
 * Message for cursor position updates
 */
class CursorPositionMessage extends BaseWebSocketMessage {
    private int cursorPosition;
    private int selectionStart;
    private int selectionEnd;
    private String userColor;
    private String userName;

    public CursorPositionMessage() {
        super();
        setMessageType("CURSOR_UPDATE");
    }

    public int getCursorPosition() {
        return cursorPosition;
    }

    public void setCursorPosition(int cursorPosition) {
        this.cursorPosition = cursorPosition;
    }

    public int getSelectionStart() {
        return selectionStart;
    }

    public void setSelectionStart(int selectionStart) {
        this.selectionStart = selectionStart;
    }

    public int getSelectionEnd() {
        return selectionEnd;
    }

    public void setSelectionEnd(int selectionEnd) {
        this.selectionEnd = selectionEnd;
    }

    public String getUserColor() {
        return userColor;
    }

    public void setUserColor(String userColor) {
        this.userColor = userColor;
    }

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }
}

/**
 * Message for user presence (joining/leaving)
 */
class UserPresenceMessage extends BaseWebSocketMessage {
    private boolean isJoining;
    private String userName;
    private String userColor;

    public UserPresenceMessage() {
        super();
        setMessageType("PRESENCE_UPDATE");
    }

    public boolean isJoining() {
        return isJoining;
    }

    public void setJoining(boolean joining) {
        isJoining = joining;
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
}

/**
 * Message for typing indicator
 */
class TypingIndicatorMessage extends BaseWebSocketMessage {
    private boolean isTyping;

    public TypingIndicatorMessage() {
        super();
        setMessageType("TYPING_INDICATOR");
    }

    public boolean isTyping() {
        return isTyping;
    }

    public void setTyping(boolean typing) {
        isTyping = typing;
    }
}

/**
 * Message for note comments
 */
class CommentMessage extends BaseWebSocketMessage {
    private String commentId;
    private int startPosition;
    private int endPosition;
    private String commentText;
    private String action;  // "add", "update", "delete"

    public CommentMessage() {
        super();
        setMessageType("COMMENT");
        this.commentId = java.util.UUID.randomUUID().toString();
    }

    public String getCommentId() {
        return commentId;
    }

    public void setCommentId(String commentId) {
        this.commentId = commentId;
    }

    public int getStartPosition() {
        return startPosition;
    }

    public void setStartPosition(int startPosition) {
        this.startPosition = startPosition;
    }

    public int getEndPosition() {
        return endPosition;
    }

    public void setEndPosition(int endPosition) {
        this.endPosition = endPosition;
    }

    public String getCommentText() {
        return commentText;
    }

    public void setCommentText(String commentText) {
        this.commentText = commentText;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }
}

/**
 * Message for error notifications
 */
class ErrorMessage extends BaseWebSocketMessage {
    private String errorCode;
    private String errorMessage;
    private String originalMessageId;

    public ErrorMessage() {
        super();
        setMessageType("ERROR");
    }

    public String getErrorCode() {
        return errorCode;
    }

    public void setErrorCode(String errorCode) {
        this.errorCode = errorCode;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }

    public String getOriginalMessageId() {
        return originalMessageId;
    }

    public void setOriginalMessageId(String originalMessageId) {
        this.originalMessageId = originalMessageId;
    }
}

/**
 * Message for initial note state synchronization
 */
class NoteStateMessage extends BaseWebSocketMessage {
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
        }

        public String getDisplayName() {
            return userName;
        }

        public void setDisplayName(String displayName) {
        }
    }
}