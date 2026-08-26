package com.pubsub.assignment.service;

import com.pubsub.assignment.config.RetryProperties;
import com.pubsub.assignment.exception.InvalidBase64Exception;
import com.pubsub.assignment.exception.PublishingException;
import com.pubsub.assignment.model.json.FailedMessage;
import com.pubsub.assignment.model.json.InputMessage;
import com.pubsub.assignment.model.json.OrderJson;
import com.pubsub.assignment.model.json.OutputMessage;
import com.pubsub.assignment.publisher.OrderPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderProcessingServiceTest {

    @Mock
    private OrderTransformationService transformationService;

    @Mock
    private OrderPublisher orderPublisher;

    @Spy
    private RetryProperties retryProperties = new RetryProperties();

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-08-25T12:00:00Z"), ZoneOffset.UTC);

    @InjectMocks
    private OrderProcessingService orderProcessingService;

    private InputMessage inputMessage;
    private OrderJson mockOrderJson;

    @BeforeEach
    void setUp() {
        retryProperties.setMaxAttempts(3);
        retryProperties.setBackoffMs(10);

        inputMessage = new InputMessage();
        inputMessage.setMessageId("msg-123");
        inputMessage.setTimestamp("2026-08-25T12:00:00Z");
        inputMessage.setDocument("validBase64XmlString");

        mockOrderJson = new OrderJson();
        mockOrderJson.setOrderId("12347");
    }

    @Test
    void shouldProcessAndPublishOrderSuccessfully() {
        when(transformationService.transform("validBase64XmlString", "msg-123"))
                .thenReturn(mockOrderJson);
        when(orderPublisher.publish(any())).thenReturn(CompletableFuture.completedFuture("msg-123"));

        CompletableFuture<Void> result = orderProcessingService.processOrder(inputMessage);
        result.join();

        ArgumentCaptor<OutputMessage> captor = ArgumentCaptor.forClass(OutputMessage.class);
        verify(transformationService, times(1)).transform("validBase64XmlString", "msg-123");
        verify(orderPublisher, times(1)).publish(captor.capture());
        verify(orderPublisher, never()).publishToDlq(any());

        OutputMessage publishedMessage = captor.getValue();
        assertThat(publishedMessage.getMessageId()).isEqualTo("msg-123");
        assertThat(publishedMessage.getTransformedAt()).isEqualTo("2026-08-25T12:00:00Z");
        assertThat(publishedMessage.getOrder()).isEqualTo(mockOrderJson);
    }

    @Test
    void shouldNotRetryAndRouteToDlqWhenTransformationFailsNonRetriable() {
        when(transformationService.transform(any(), eq("msg-123")))
                .thenThrow(new InvalidBase64Exception("Document is not a valid Base64 string.", "msg-123"));
        when(orderPublisher.publishToDlq(any()))
                .thenReturn(CompletableFuture.completedFuture("dlq-ack-id"));

        CompletableFuture<Void> result = orderProcessingService.processOrder(inputMessage);

        assertThatThrownBy(result::join)
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(InvalidBase64Exception.class);

        verify(transformationService, times(1)).transform(any(), any());
        verify(orderPublisher, never()).publish(any());

        ArgumentCaptor<FailedMessage> dlqCaptor = ArgumentCaptor.forClass(FailedMessage.class);
        verify(orderPublisher, times(1)).publishToDlq(dlqCaptor.capture());

        FailedMessage failedMessage = dlqCaptor.getValue();
        assertThat(failedMessage.getMessageId()).isEqualTo("msg-123");
        assertThat(failedMessage.getReason()).isEqualTo("Document is not a valid Base64 string.");
    }

    @Test
    void shouldRetryMaxAttemptsAndRouteToDlqWhenPublishingFailsConsistently() {
        when(transformationService.transform(any(), eq("msg-123"))).thenReturn(mockOrderJson);
        when(orderPublisher.publish(any())).thenReturn(
                CompletableFuture.failedFuture(new PublishingException("Failed to publish to Pub/Sub", "msg-123", new RuntimeException()))
        );
        when(orderPublisher.publishToDlq(any()))
                .thenReturn(CompletableFuture.completedFuture("dlq-ack-id"));

        CompletableFuture<Void> result = orderProcessingService.processOrder(inputMessage);

        assertThatThrownBy(result::join)
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(PublishingException.class);

        verify(transformationService, times(3)).transform(any(), any());
        verify(orderPublisher, times(3)).publish(any());
        verify(orderPublisher, times(1)).publishToDlq(any(FailedMessage.class));
    }

    @Test
    void shouldSucceedOnRetryWhenTransientErrorIsResolved() {
        when(transformationService.transform(any(), eq("msg-123"))).thenReturn(mockOrderJson);
        when(orderPublisher.publish(any()))
                .thenReturn(CompletableFuture.failedFuture(new PublishingException("Transient error", "msg-123", new RuntimeException())))
                .thenReturn(CompletableFuture.completedFuture("msg-123"));

        CompletableFuture<Void> result = orderProcessingService.processOrder(inputMessage);
        result.join();

        verify(transformationService, times(2)).transform(any(), any());
        verify(orderPublisher, times(2)).publish(any());
        verify(orderPublisher, never()).publishToDlq(any());
    }
}