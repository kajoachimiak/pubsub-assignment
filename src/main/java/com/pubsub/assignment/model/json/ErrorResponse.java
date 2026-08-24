package com.pubsub.assignment.model.json;

public record ErrorResponse(
        String error,
        String message,
        String messageId
) {}