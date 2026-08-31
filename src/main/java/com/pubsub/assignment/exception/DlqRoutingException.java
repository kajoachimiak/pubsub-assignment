package com.pubsub.assignment.exception;

public class DlqRoutingException extends ProcessingException {

    public DlqRoutingException(String message, String messageId, Throwable cause) {
        super(message, messageId, cause);
    }
}
