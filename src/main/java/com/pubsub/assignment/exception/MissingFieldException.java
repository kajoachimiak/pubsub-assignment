package com.pubsub.assignment.exception;

public class MissingFieldException extends ProcessingException {
    public MissingFieldException(String message, String messageId) {
        super(message, messageId);
    }
}