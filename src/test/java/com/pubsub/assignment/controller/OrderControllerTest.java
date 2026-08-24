package com.pubsub.assignment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pubsub.assignment.exception.GlobalExceptionHandler;
import com.pubsub.assignment.exception.InvalidXmlException;
import com.pubsub.assignment.model.json.InputMessage;
import com.pubsub.assignment.service.OrderProcessingService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
@Import(GlobalExceptionHandler.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderProcessingService orderProcessingService;

    private InputMessage validMessage;

    @BeforeEach
    void setUp() {
        validMessage = new InputMessage();
        validMessage.setMessageId("msg-123");
        validMessage.setDocument("dGVzdC14bWwtY29udGVudA==");
        validMessage.setTimestamp("2026-08-24T12:00:00Z");
    }

    @Test
    void shouldReturn200OnSuccess() throws Exception {
        // given
        doNothing().when(orderProcessingService).processOrder(any(InputMessage.class));

        // when & then
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validMessage)))
                .andExpect(status().isOk());

        verify(orderProcessingService, times(1)).processOrder(any(InputMessage.class));
    }

    @Test
    void shouldReturn400WhenInvalidXmlExceptionIsThrown() throws Exception {
        // given
        doThrow(new InvalidXmlException("Malformed XML input", "msg-123", new RuntimeException("Xml error")))
                .when(orderProcessingService).processOrder(any(InputMessage.class));

        // when & then
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validMessage)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("InvalidXml"))
                .andExpect(jsonPath("$.message").value("Malformed XML input"))
                .andExpect(jsonPath("$.messageId").value("msg-123"));
    }

    @Test
    void shouldReturn400WhenInputValidationFails() throws Exception {
        // given - brak wymaganych pól messageId oraz document
        InputMessage invalidMessage = new InputMessage();

        // when & then
        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidMessage)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ValidationError"));

        verify(orderProcessingService, never()).processOrder(any());
    }
}