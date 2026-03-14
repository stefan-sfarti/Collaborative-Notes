package com.collabnotes.collabnotes.websocket.message;

/**
 * Message for cursor position updates
 */
public class CursorPositionMessage extends BaseWebSocketMessage {
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
