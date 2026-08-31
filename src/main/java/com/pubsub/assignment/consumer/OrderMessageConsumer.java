package com.pubsub.assignment.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.spring.pubsub.support.BasicAcknowledgeablePubsubMessage;
import com.google.cloud.spring.pubsub.support.GcpPubSubHeaders;
import com.pubsub.assignment.exception.ProcessingException;
import com.pubsub.assignment.model.json.InputMessage;
import com.pubsub.assignment.service.OrderProcessingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletionException;

@Slf4j
@Component
@RequiredArgsConstructor
public class OrderMessageConsumer {

    private final OrderProcessingService orderProcessingService;
    private final ObjectMapper objectMapper;

    @ServiceActivator(inputChannel = "inputMessageChannel")
    public void handleMessage(Message<byte[]> message) {
        BasicAcknowledgeablePubsubMessage ackable =
                GcpPubSubHeaders.getOriginalMessage(message).orElse(null);
        byte[] payload = message.getPayload();

        InputMessage inputMessage;
        try {
            inputMessage = objectMapper.readValue(payload, InputMessage.class);
        } catch (Exception e) {
            handleUnparseable(ackable, payload, "Message payload could not be parsed to an order envelope.");
            return;
        }

        if (!StringUtils.hasText(inputMessage.getMessageId()) || !StringUtils.hasText(inputMessage.getDocument())) {
            handleUnparseable(ackable, payload, "Message envelope is missing required messageId or document.");
            return;
        }

        try (var mdc = MDC.putCloseable("messageId", inputMessage.getMessageId())) {
            log.info("Received Pub/Sub message from subscription");
        }

        orderProcessingService.processOrder(inputMessage)
                .whenComplete((result, ex) -> {
                    if (ackable == null) {
                        return;
                    }
                    if (ex == null) {
                        ackable.ack();
                    } else if (unwrap(ex) instanceof ProcessingException) {
                        ackable.ack();
                    } else {
                        ackable.nack();
                    }
                });
    }

    private void handleUnparseable(BasicAcknowledgeablePubsubMessage ackable, byte[] payload, String reason) {
        String fallbackId = (ackable != null) ? ackable.getPubsubMessage().getMessageId() : "unknown";
        String rawDocument = new String(payload, StandardCharsets.UTF_8);

        orderProcessingService.routeToDlq(fallbackId, rawDocument, reason)
                .whenComplete((result, ex) -> {
                    if (ackable == null) {
                        return;
                    }
                    if (ex == null) {
                        ackable.ack();
                    } else {
                        ackable.nack();
                    }
                });
    }

    private Throwable unwrap(Throwable ex) {
        return (ex instanceof CompletionException && ex.getCause() != null) ? ex.getCause() : ex;
    }
}
