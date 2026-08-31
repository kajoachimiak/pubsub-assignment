package com.pubsub.assignment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.pubsub.assignment.exception.GlobalExceptionHandler;
import com.pubsub.assignment.exception.InvalidBase64Exception;
import com.pubsub.assignment.exception.InvalidXmlException;
import com.pubsub.assignment.exception.MissingFieldException;
import com.pubsub.assignment.exception.PublishingException;
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
import org.springframework.test.web.servlet.MvcResult;

import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
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
        validMessage.setMessageId("7a1e78c9");
        validMessage.setDocument("dGVzdC14bWwtY29udGVudA==");
        validMessage.setTimestamp("2026-07-27T12:00:00Z");
    }

    @Test
    void shouldReturn200OnSuccess() throws Exception {
        // given
        when(orderProcessingService.processOrder(any(InputMessage.class)))
                .thenReturn(CompletableFuture.completedFuture(null));

        // when
        MvcResult mvcResult = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validMessage)))
                .andExpect(request().asyncStarted())
                .andReturn();

        // then
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isOk());

        verify(orderProcessingService, times(1)).processOrder(any(InputMessage.class));
    }

    @Test
    void shouldReturn400WhenInvalidBase64ExceptionIsThrown() throws Exception {
        // given
        InvalidBase64Exception ex = new InvalidBase64Exception("Document is not a valid Base64 string.", "7a1e78c9");
        when(orderProcessingService.processOrder(any(InputMessage.class)))
                .thenReturn(CompletableFuture.failedFuture(ex));

        // when
        MvcResult mvcResult = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validMessage)))
                .andExpect(request().asyncStarted())
                .andReturn();

        // then
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("InvalidBase64"))
                .andExpect(jsonPath("$.message").value("Document is not a valid Base64 string."))
                .andExpect(jsonPath("$.messageId").value("7a1e78c9"));
    }

    @Test
    void shouldReturn400WhenInvalidXmlExceptionIsThrown() throws Exception {
        // given
        InvalidXmlException ex = new InvalidXmlException("Message could not be parsed from XML to JSON.", "7a1e78c9", new RuntimeException());
        when(orderProcessingService.processOrder(any(InputMessage.class)))
                .thenReturn(CompletableFuture.failedFuture(ex));

        // when
        MvcResult mvcResult = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validMessage)))
                .andExpect(request().asyncStarted())
                .andReturn();

        // then
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("InvalidXml"))
                .andExpect(jsonPath("$.message").value("Message could not be parsed from XML to JSON."))
                .andExpect(jsonPath("$.messageId").value("7a1e78c9"));
    }

    @Test
    void shouldReturn400WhenInvalidXmlExceptionHasNullCause() throws Exception {
        // given
        InvalidXmlException ex = new InvalidXmlException("Message could not be parsed from XML to JSON.", "7a1e78c9", null);
        when(orderProcessingService.processOrder(any(InputMessage.class)))
                .thenReturn(CompletableFuture.failedFuture(ex));

        // when
        MvcResult mvcResult = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validMessage)))
                .andExpect(request().asyncStarted())
                .andReturn();

        // then
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("InvalidXml"))
                .andExpect(jsonPath("$.message").value("Message could not be parsed from XML to JSON."))
                .andExpect(jsonPath("$.messageId").value("7a1e78c9"));
    }

    @Test
    void shouldReturn400WhenMissingFieldExceptionIsThrown() throws Exception {
        // given
        MissingFieldException ex = new MissingFieldException("Order ID is required.", "7a1e78c9");
        when(orderProcessingService.processOrder(any(InputMessage.class)))
                .thenReturn(CompletableFuture.failedFuture(ex));

        // when
        MvcResult mvcResult = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validMessage)))
                .andExpect(request().asyncStarted())
                .andReturn();

        // then
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("ValidationError"))
                .andExpect(jsonPath("$.message").value("Order ID is required."))
                .andExpect(jsonPath("$.messageId").value("7a1e78c9"));
    }

    @Test
    void shouldReturn502WhenPublishingExceptionIsThrown() throws Exception {
        // given
        PublishingException ex = new PublishingException("Failed to publish to Pub/Sub", "7a1e78c9", new RuntimeException());
        when(orderProcessingService.processOrder(any(InputMessage.class)))
                .thenReturn(CompletableFuture.failedFuture(ex));

        // when
        MvcResult mvcResult = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validMessage)))
                .andExpect(request().asyncStarted())
                .andReturn();

        // then
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("PublishingError"))
                .andExpect(jsonPath("$.message").value("Failed to publish to Pub/Sub"))
                .andExpect(jsonPath("$.messageId").value("7a1e78c9"));
    }

    @Test
    void shouldReturn500WhenUnexpectedErrorOccurs() throws Exception {
        // given
        when(orderProcessingService.processOrder(any(InputMessage.class)))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("Some random database or network error")));

        // when
        MvcResult mvcResult = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validMessage)))
                .andExpect(request().asyncStarted())
                .andReturn();

        // then
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("ServerError"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred."))
                .andExpect(jsonPath("$.messageId").value("unknown"));
    }

    @Test
    void shouldReturn500WithCorrelatedMessageIdWhenCauseIsProcessingException() throws Exception {
        // given
        MissingFieldException cause = new MissingFieldException("Order ID is required.", "7a1e78c9");
        RuntimeException ex = new RuntimeException("Wrapped failure", cause);
        when(orderProcessingService.processOrder(any(InputMessage.class)))
                .thenReturn(CompletableFuture.failedFuture(ex));

        // when
        MvcResult mvcResult = mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validMessage)))
                .andExpect(request().asyncStarted())
                .andReturn();

        // then
        mockMvc.perform(asyncDispatch(mvcResult))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").value("ServerError"))
                .andExpect(jsonPath("$.message").value("An unexpected error occurred."))
                .andExpect(jsonPath("$.messageId").value("7a1e78c9"));
    }

    @Test
    void shouldReturn400WhenInputValidationFailsForEmptyPayload() throws Exception {
        // given
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