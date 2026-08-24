package com.pubsub.assignment.exception;

public class InvalidBase64Exception extends RuntimeException {
    public InvalidBase64Exception(String message) {
        super(message);
    }
}
