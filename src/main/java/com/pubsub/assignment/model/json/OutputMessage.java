package com.pubsub.assignment.model.json;

import lombok.Data;

@Data
public class OutputMessage {
    private String messageId;
    private String transformedAt;
    private OrderJson order;
}
