package com.pubsub.assignment.exception;

public class InvalidBase64Exception extends ProcessingException {
    public InvalidBase64Exception(String message, String messageId) {
        super(message, messageId);
    }
}