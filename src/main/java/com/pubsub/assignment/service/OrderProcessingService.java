package com.pubsub.assignment.service;

import com.pubsub.assignment.config.RetryProperties;
import com.pubsub.assignment.exception.DlqRoutingException;
import com.pubsub.assignment.exception.InvalidBase64Exception;
import com.pubsub.assignment.exception.InvalidXmlException;
import com.pubsub.assignment.exception.MissingFieldException;
import com.pubsub.assignment.model.json.FailedMessage;
import com.pubsub.assignment.model.json.InputMessage;
import com.pubsub.assignment.model.json.OrderJson;
import com.pubsub.assignment.model.json.OutputMessage;
import com.pubsub.assignment.publisher.OrderPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderProcessingService {

    private final OrderTransformationService transformationService;
    private final OrderPublisher orderPublisher;
    private final IdempotencyService idempotencyService;
    private final RetryProperties retryProperties;
    private final Clock clock;
    private final MeterRegistry meterRegistry;
    private final Executor processingExecutor;

    public CompletableFuture<Void> processOrder(InputMessage inputMessage) {
        long startTime = System.currentTimeMillis();
        String messageId = inputMessage.getMessageId();

        if (idempotencyService.register(messageId)) {
            try (var mdc = MDC.putCloseable("messageId", messageId)) {
                log.info("Duplicate message detected and ignored");
            }
            return CompletableFuture.completedFuture(null);
        }

        return CompletableFuture.supplyAsync(() -> transformOrHandleFailure(inputMessage), processingExecutor)
                .thenCompose(future -> future)
                .whenComplete((result, ex) -> {
                    long duration = System.currentTimeMillis() - startTime;
                    String status = (ex == null) ? "success" : "error";

                    meterRegistry.timer("order.processing.duration", "status", status)
                            .record(duration, TimeUnit.MILLISECONDS);

                    try (var mdc = MDC.putCloseable("messageId", messageId)) {
                        if (ex == null) {
                            log.info("Processing completed successfully in {} ms", duration);
                        } else if (unwrap(ex) instanceof DlqRoutingException) {
                            idempotencyService.unregister(messageId);
                            log.error("Processing failed and DLQ routing failed after {} ms; message will be redelivered", duration);
                        } else {
                            log.error("Processing failed after {} ms; message routed to DLQ", duration);
                        }
                    }
                });
    }

    private CompletableFuture<Void> transformOrHandleFailure(InputMessage inputMessage) {
        try {
            OutputMessage outputMessage = transformToOutputMessage(inputMessage);
            return processWithRetry(inputMessage, outputMessage, 1);
        } catch (Exception e) {
            return handleFailure(inputMessage, e);
        }
    }

    private OutputMessage transformToOutputMessage(InputMessage inputMessage) {
        String messageId = inputMessage.getMessageId();
        OrderJson orderJson = transformationService.transform(inputMessage.getDocument(), messageId);
        return createOutputMessage(messageId, orderJson);
    }

    private CompletableFuture<Void> processWithRetry(InputMessage inputMessage, OutputMessage outputMessage, int attempt) {
        String messageId = inputMessage.getMessageId();

        try (var mdc = MDC.putCloseable("messageId", messageId)) {
            log.info("Processing message (attempt {}/{})", attempt, retryProperties.getMaxAttempts());
        }

        return executeSingleAttempt(messageId, outputMessage)
                .exceptionallyCompose(ex -> {
                    Throwable cause = unwrap(ex);

                    if (isRetriable(cause) && attempt < retryProperties.getMaxAttempts()) {
                        try (var mdc = MDC.putCloseable("messageId", messageId)) {
                            log.warn("Attempt {} failed. Retrying in {} ms. Cause: {}",
                                    attempt, retryProperties.getBackoffMs(), cause.getMessage());
                        }

                        return CompletableFuture.runAsync(() -> {},
                                        CompletableFuture.delayedExecutor(retryProperties.getBackoffMs(), TimeUnit.MILLISECONDS))
                                .thenCompose(v -> processWithRetry(inputMessage, outputMessage, attempt + 1));
                    }

                    return handleFailure(inputMessage, cause);
                });
    }

    private CompletableFuture<Void> executeSingleAttempt(String messageId, OutputMessage outputMessage) {
        try {
            return orderPublisher.publish(outputMessage)
                    .thenAccept(result -> {
                        try (var mdc = MDC.putCloseable("messageId", messageId)) {
                            log.info("Message transformed and published");
                        }
                    });
        } catch (Exception e) {
            return CompletableFuture.failedFuture(e);
        }
    }

    private boolean isRetriable(Throwable cause) {
        return !(cause instanceof InvalidBase64Exception
                || cause instanceof InvalidXmlException
                || cause instanceof MissingFieldException);
    }

    private CompletableFuture<Void> handleFailure(InputMessage inputMessage, Throwable cause) {
        try (var mdc = MDC.putCloseable("messageId", inputMessage.getMessageId())) {
            log.warn("Routing to DLQ. Reason: {}", cause.getMessage());
        }

        FailedMessage failedMessage = FailedMessage.builder()
                .messageId(inputMessage.getMessageId())
                .failedAt(nowIso())
                .reason(cause.getMessage())
                .rawDocument(inputMessage.getDocument())
                .build();

        return orderPublisher.publishToDlq(failedMessage)
                .handle((dlqResult, dlqEx) -> dlqEx)
                .thenCompose(dlqEx -> {
                    if (dlqEx == null) {
                        return CompletableFuture.<Void>failedFuture(cause);
                    }

                    Throwable dlqCause = unwrap(dlqEx);
                    try (var mdc = MDC.putCloseable("messageId", inputMessage.getMessageId())) {
                        log.error("Failed to route message to DLQ; message will be redelivered", dlqCause);
                    }

                    return CompletableFuture.<Void>failedFuture(
                            new DlqRoutingException("Failed to route message to DLQ.", inputMessage.getMessageId(), dlqCause));
                });
    }

    public CompletableFuture<Void> routeToDlq(String messageId, String rawDocument, String reason) {
        try (var mdc = MDC.putCloseable("messageId", messageId)) {
            log.warn("Routing malformed message to DLQ. Reason: {}", reason);
        }

        FailedMessage failedMessage = FailedMessage.builder()
                .messageId(messageId)
                .failedAt(nowIso())
                .reason(reason)
                .rawDocument(rawDocument)
                .build();

        return orderPublisher.publishToDlq(failedMessage).thenAccept(dlqResult -> {});
    }

    private OutputMessage createOutputMessage(String messageId, OrderJson orderJson) {
        OutputMessage outputMessage = new OutputMessage();
        outputMessage.setMessageId(messageId);
        outputMessage.setTransformedAt(nowIso());
        outputMessage.setOrder(orderJson);
        return outputMessage;
    }

    private String nowIso() {
        return DateTimeFormatter.ISO_INSTANT.format(Instant.now(clock).atOffset(ZoneOffset.UTC));
    }

    private Throwable unwrap(Throwable ex) {
        return (ex instanceof CompletionException && ex.getCause() != null) ? ex.getCause() : ex;
    }
}