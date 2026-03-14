package com.collabnotes.collabnotes.websocket.message;

/**
 * Message for typing indicator
 */
public class TypingIndicatorMessage extends BaseWebSocketMessage {
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
