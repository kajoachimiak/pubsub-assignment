package com.pubsub.assignment.service;

import com.pubsub.assignment.exception.InvalidBase64Exception;
import com.pubsub.assignment.exception.PublishingException;
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

    // Zamrażamy czas w teście, aby wynik transformedAt był w 100% przewidywalny
    @Spy
    private Clock clock = Clock.fixed(Instant.parse("2026-08-24T12:00:00Z"), ZoneOffset.UTC);

    @InjectMocks
    private OrderProcessingService orderProcessingService;

    private InputMessage inputMessage;
    private OrderJson mockOrderJson;

    @BeforeEach
    void setUp() {
        inputMessage = new InputMessage();
        inputMessage.setMessageId("msg-123");
        inputMessage.setTimestamp("2026-08-24T12:00:00Z");
        inputMessage.setDocument("validBase64XmlString");

        mockOrderJson = new OrderJson();
        mockOrderJson.setOrderId("12347");
    }

    @Test
    void shouldProcessAndPublishOrderSuccessfully() {
        // given
        when(transformationService.transform("validBase64XmlString", "msg-123"))
                .thenReturn(mockOrderJson);

        // when
        orderProcessingService.processOrder(inputMessage);

        // then
        ArgumentCaptor<OutputMessage> captor = ArgumentCaptor.forClass(OutputMessage.class);
        verify(transformationService, times(1)).transform("validBase64XmlString", "msg-123");
        verify(orderPublisher, times(1)).publish(captor.capture());

        OutputMessage publishedMessage = captor.getValue();
        assertThat(publishedMessage.getMessageId()).isEqualTo("msg-123");
        assertThat(publishedMessage.getTransformedAt()).isEqualTo("2026-08-24T12:00:00Z");
        assertThat(publishedMessage.getOrder()).isEqualTo(mockOrderJson);
    }

    @Test
    void shouldPropagateExceptionWhenTransformationFails() {
        // given
        when(transformationService.transform(any(), eq("msg-123")))
                .thenThrow(new InvalidBase64Exception("Invalid Base64 payload", "msg-123"));

        // when & then
        assertThatThrownBy(() -> orderProcessingService.processOrder(inputMessage))
                .isInstanceOf(InvalidBase64Exception.class)
                .hasMessage("Invalid Base64 payload");

        verify(orderPublisher, never()).publish(any());
    }

    @Test
    void shouldPropagateExceptionWhenPublishingFails() {
        // given
        when(transformationService.transform(any(), eq("msg-123"))).thenReturn(mockOrderJson);

        doThrow(new PublishingException("Publishing error", "msg-123", new RuntimeException()))
                .when(orderPublisher).publish(any());

        // when & then
        assertThatThrownBy(() -> orderProcessingService.processOrder(inputMessage))
                .isInstanceOf(PublishingException.class)
                .hasMessage("Publishing error");

        verify(transformationService, times(1)).transform("validBase64XmlString", "msg-123");
    }
}