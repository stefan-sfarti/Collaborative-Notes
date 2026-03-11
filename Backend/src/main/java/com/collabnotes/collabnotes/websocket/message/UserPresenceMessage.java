package com.collabnotes.collabnotes.websocket.message;

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