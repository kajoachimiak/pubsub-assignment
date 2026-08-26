package com.pubsub.assignment.model.json;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FailedMessage {
    private String messageId;
    private String failedAt;
    private String reason;
    private String rawDocument;
}