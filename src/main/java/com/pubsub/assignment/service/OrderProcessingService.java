package com.pubsub.assignment.service;

import com.pubsub.assignment.model.json.InputMessage;
import com.pubsub.assignment.model.json.OrderJson;
import com.pubsub.assignment.model.json.OutputMessage;
import com.pubsub.assignment.publisher.OrderPublisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderProcessingService {

    private final OrderTransformationService transformationService;
    private final OrderPublisher orderPublisher;
    private final Clock clock;

    public CompletableFuture<Void> processOrder(InputMessage inputMessage) {
        String messageId = inputMessage.getMessageId();
        log.info("Starting processing for message ID: {}", messageId);

        OrderJson orderJson;
        try {
            orderJson = transformationService.transform(inputMessage.getDocument(), messageId);
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }

        OutputMessage outputMessage = createOutputMessage(messageId, orderJson);

        return orderPublisher.publish(outputMessage)
                .thenAccept(result -> log.info("Successfully completed processing for message ID: {}", messageId));
    }

    private OutputMessage createOutputMessage(String messageId, OrderJson orderJson) {
        OutputMessage outputMessage = new OutputMessage();
        outputMessage.setMessageId(messageId);
        outputMessage.setTransformedAt(DateTimeFormatter.ISO_INSTANT.format(Instant.now(clock).atOffset(ZoneOffset.UTC)));
        outputMessage.setOrder(orderJson);
        return outputMessage;
    }
}