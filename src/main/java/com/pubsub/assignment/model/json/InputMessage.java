package com.pubsub.assignment.model.json;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class InputMessage {
    @NotBlank(message = "messageId is required")
    private String messageId;

    private String timestamp;

    @NotBlank(message = "document is required")
    private String document;
}