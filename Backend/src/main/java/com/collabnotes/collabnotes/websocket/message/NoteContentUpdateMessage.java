package com.collabnotes.collabnotes.websocket.message;

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

