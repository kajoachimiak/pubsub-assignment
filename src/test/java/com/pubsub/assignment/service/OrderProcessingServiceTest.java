package com.pubsub.assignment.service;

import com.pubsub.assignment.config.RetryProperties;
import com.pubsub.assignment.exception.DlqRoutingException;
import com.pubsub.assignment.exception.InvalidBase64Exception;
import com.pubsub.assignment.exception.PublishingException;
import com.pubsub.assignment.model.json.FailedMessage;
import com.pubsub.assignment.model.json.InputMessage;
import com.pubsub.assignment.model.json.OrderJson;
import com.pubsub.assignment.model.json.OutputMessage;
import com.pubsub.assignment.publisher.OrderPublisher;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
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
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;

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
    private IdempotencyService idempotencyService = new IdempotencyService();

    @Spy
    private RetryProperties retryProperties = new RetryProperties();

    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-08-25T12:00:00Z"), ZoneOffset.UTC);

    @Spy
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    @Mock
    private Executor processingExecutor;

    @InjectMocks
    private OrderProcessingService orderProcessingService;

    private InputMessage inputMessage;
    private OrderJson mockOrderJson;

    @BeforeEach
    void setUp() {
        retryProperties.setMaxAttempts(3);
        retryProperties.setBackoffMs(10);
        meterRegistry.clear();

        lenient().doAnswer(invocation -> {
            Runnable task = invocation.getArgument(0);
            task.run();
            return null;
        }).when(processingExecutor).execute(any(Runnable.class));

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

        assertThat(meterRegistry.timer("order.processing.duration", "status", "success").count()).isEqualTo(1);
    }

    @Test
    void shouldIgnoreDuplicateMessage() {
        when(idempotencyService.register("msg-123")).thenReturn(true);

        CompletableFuture<Void> result = orderProcessingService.processOrder(inputMessage);
        result.join();

        verify(transformationService, never()).transform(any(), any());
        verify(orderPublisher, never()).publish(any());
        verify(orderPublisher, never()).publishToDlq(any());
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
        verify(idempotencyService, never()).unregister("msg-123");

        FailedMessage failedMessage = dlqCaptor.getValue();
        assertThat(failedMessage.getMessageId()).isEqualTo("msg-123");
        assertThat(failedMessage.getReason()).isEqualTo("Document is not a valid Base64 string.");

        assertThat(meterRegistry.timer("order.processing.duration", "status", "error").count()).isEqualTo(1);
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

        verify(transformationService, times(1)).transform(any(), any());
        verify(orderPublisher, times(3)).publish(any());
        verify(orderPublisher, times(1)).publishToDlq(any(FailedMessage.class));
        verify(idempotencyService, never()).unregister("msg-123");

        assertThat(meterRegistry.timer("order.processing.duration", "status", "error").count()).isEqualTo(1);
    }

    @Test
    void shouldThrowDlqRoutingExceptionAndUnregisterWhenDlqRoutingFails() {
        when(transformationService.transform(any(), eq("msg-123")))
                .thenThrow(new InvalidBase64Exception("Document is not a valid Base64 string.", "msg-123"));
        when(orderPublisher.publishToDlq(any()))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("DLQ publish failed")));

        CompletableFuture<Void> result = orderProcessingService.processOrder(inputMessage);

        assertThatThrownBy(result::join)
                .isInstanceOf(CompletionException.class)
                .hasCauseInstanceOf(DlqRoutingException.class);

        verify(transformationService, times(1)).transform(any(), any());
        verify(orderPublisher, never()).publish(any());
        verify(orderPublisher, times(1)).publishToDlq(any(FailedMessage.class));
        verify(idempotencyService, times(1)).unregister("msg-123");

        assertThat(meterRegistry.timer("order.processing.duration", "status", "error").count()).isEqualTo(1);
    }

    @Test
    void shouldSucceedOnRetryWhenTransientErrorIsResolved() {
        when(transformationService.transform(any(), eq("msg-123"))).thenReturn(mockOrderJson);
        when(orderPublisher.publish(any()))
                .thenReturn(CompletableFuture.failedFuture(new PublishingException("Transient error", "msg-123", new RuntimeException())))
                .thenReturn(CompletableFuture.completedFuture("msg-123"));

        CompletableFuture<Void> result = orderProcessingService.processOrder(inputMessage);
        result.join();

        verify(transformationService, times(1)).transform(any(), any());
        verify(orderPublisher, times(2)).publish(any());
        verify(orderPublisher, never()).publishToDlq(any());

        assertThat(meterRegistry.timer("order.processing.duration", "status", "success").count()).isEqualTo(1);
    }

    @Test
    void shouldDispatchInitialProcessingAttemptViaProcessingExecutor() {
        when(transformationService.transform(any(), eq("msg-123"))).thenReturn(mockOrderJson);
        when(orderPublisher.publish(any())).thenReturn(CompletableFuture.completedFuture("msg-123"));

        orderProcessingService.processOrder(inputMessage).join();

        verify(processingExecutor, times(1)).execute(any(Runnable.class));
    }

    @Test
    void shouldExecuteInitialProcessingAttemptOffTheCallingThread() {
        Executor realExecutor = Executors.newVirtualThreadPerTaskExecutor();
        OrderProcessingService serviceWithRealExecutor = new OrderProcessingService(
                transformationService, orderPublisher, idempotencyService, retryProperties, clock, meterRegistry, realExecutor);

        Thread callingThread = Thread.currentThread();
        AtomicReference<Thread> executingThread = new AtomicReference<>();

        when(transformationService.transform(any(), eq("msg-123"))).thenAnswer(invocation -> {
            executingThread.set(Thread.currentThread());
            return mockOrderJson;
        });
        when(orderPublisher.publish(any())).thenReturn(CompletableFuture.completedFuture("msg-123"));

        serviceWithRealExecutor.processOrder(inputMessage).join();

        assertThat(executingThread.get()).isNotNull();
        assertThat(executingThread.get()).isNotSameAs(callingThread);
        assertThat(executingThread.get().isVirtual()).isTrue();
    }
}