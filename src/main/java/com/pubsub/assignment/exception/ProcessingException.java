package com.pubsub.assignment.exception;

import lombok.Getter;

@Getter
public abstract class ProcessingException extends RuntimeException {
    private final String messageId;

    protected ProcessingException(String message, String messageId) {
        super(message);
        this.messageId = messageId;
    }

    protected ProcessingException(String message, String messageId, Throwable cause) {
        super(message, cause);
        this.messageId = messageId;
    }
}