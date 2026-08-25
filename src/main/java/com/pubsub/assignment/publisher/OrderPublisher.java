package com.pubsub.assignment.publisher;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.core.PubSubTemplate;
import com.pubsub.assignment.exception.PublishingException;
import com.pubsub.assignment.model.json.OutputMessage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.CompletableFuture;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderPublisher {

    private final PubSubTemplate pubSubTemplate;
    private final ObjectMapper objectMapper;

    @Value("${app.pubsub.output-topic:orders.transformed}")
    private String outputTopic;

    public CompletableFuture<String> publish(OutputMessage message) {
        try {
            String payload = objectMapper.writeValueAsString(message);

            return pubSubTemplate.publish(outputTopic, payload)
                    .whenComplete((messageId, ex) -> {
                        if (ex == null) {
                            log.debug("Message {} successfully published to topic {}", message.getMessageId(), outputTopic);
                        } else {
                            log.error("Failed to publish message with ID: {} to GCP Pub/Sub", message.getMessageId(), ex);
                        }
                    })
                    .exceptionally(ex -> {
                        throw new PublishingException("Failed to publish to Pub/Sub", message.getMessageId(), ex);
                    });

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize message with ID: {}", message.getMessageId(), e);
            return CompletableFuture.failedFuture(new PublishingException("Failed to serialize output message", message.getMessageId(), e));
        }
    }
}