package com.pubsub.assignment.exception;

public class PublishingException extends ProcessingException {

    public PublishingException(String message, String messageId, Throwable cause) {
        super(message, messageId, cause);
    }
}
