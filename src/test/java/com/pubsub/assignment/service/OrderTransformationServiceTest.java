package com.pubsub.assignment.service;

import com.pubsub.assignment.exception.InvalidBase64Exception;
import com.pubsub.assignment.exception.InvalidXmlException;
import com.pubsub.assignment.exception.MissingFieldException;
import com.pubsub.assignment.mapper.OrderMapper;
import com.pubsub.assignment.model.json.OrderJson;
import com.pubsub.assignment.model.xml.OrderXml;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderTransformationServiceTest {

    @Mock
    private OrderMapper orderMapper;

    @InjectMocks
    private OrderTransformationService service;

    @Test
    void shouldSuccessfullyTransformValidPayload() {
        // given
        String messageId = "msg-123";
        String validXml = "<Order><ID>12345</ID></Order>";
        String base64Payload = Base64.getEncoder().encodeToString(validXml.getBytes());

        OrderJson mappedJson = new OrderJson();
        mappedJson.setOrderId("12345");

        when(orderMapper.toOrderJson(any(OrderXml.class))).thenReturn(mappedJson);

        // when
        OrderJson result = service.transform(base64Payload, messageId);

        // then
        assertNotNull(result);
        assertEquals("12345", result.getOrderId());
        verify(orderMapper, times(1)).toOrderJson(any(OrderXml.class));
    }

    @Test
    void shouldThrowInvalidBase64ExceptionWhenPayloadIsNotBase64() {
        // given
        String invalidBase64 = "To nie jest poprawny Base64!@#";
        String messageId = "msg-123";

        // when & then
        assertThrows(InvalidBase64Exception.class, () -> {
            service.transform(invalidBase64, messageId);
        });

        verifyNoInteractions(orderMapper);
    }

    @Test
    void shouldThrowInvalidXmlExceptionWhenXmlIsMalformed() {
        // given
        String messageId = "msg-123";
        String malformedXml = "<Order><ID>12345</ID>";
        String base64Payload = Base64.getEncoder().encodeToString(malformedXml.getBytes());

        // when & then
        assertThrows(InvalidXmlException.class, () -> {
            service.transform(base64Payload, messageId);
        });

        verifyNoInteractions(orderMapper);
    }

    @Test
    void shouldThrowMissingFieldExceptionWhenOrderIdIsMissing() {
        // given
        String messageId = "msg-123";
        String validXml = "<Order></Order>";
        String base64Payload = Base64.getEncoder().encodeToString(validXml.getBytes());

        OrderJson mappedJson = new OrderJson();

        when(orderMapper.toOrderJson(any(OrderXml.class))).thenReturn(mappedJson);

        // when & then
        assertThrows(MissingFieldException.class, () -> {
            service.transform(base64Payload, messageId);
        });

        verify(orderMapper, times(1)).toOrderJson(any(OrderXml.class));
    }
}