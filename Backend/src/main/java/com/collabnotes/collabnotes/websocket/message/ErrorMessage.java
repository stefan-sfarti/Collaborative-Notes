package com.collabnotes.collabnotes.websocket.message;

/**
 * Message for error notifications
 */
public class ErrorMessage extends BaseWebSocketMessage {
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
