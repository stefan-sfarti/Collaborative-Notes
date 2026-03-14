package com.collabnotes.collabnotes.websocket.message;

/**
 * Message for note comments
 */
public class CommentMessage extends BaseWebSocketMessage {
    private String commentId;
    private int startPosition;
    private int endPosition;
    private String commentText;
    private String action; // "add", "update", "delete"

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
