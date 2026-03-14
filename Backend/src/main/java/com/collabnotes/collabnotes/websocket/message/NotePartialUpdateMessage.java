package com.collabnotes.collabnotes.websocket.message;

/**
 * Message for partial note updates
 */
public class NotePartialUpdateMessage extends BaseWebSocketMessage {
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
