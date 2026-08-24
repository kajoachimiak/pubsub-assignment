package com.pubsub.assignment.exception;

public class InvalidXmlException extends ProcessingException {
    public InvalidXmlException(String message, String messageId, Throwable cause) {
        super(message, messageId, cause);
    }
}