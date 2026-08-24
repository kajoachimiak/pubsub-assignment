package com.pubsub.assignment.controller;

import com.pubsub.assignment.model.json.InputMessage;
import com.pubsub.assignment.service.OrderProcessingService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api/orders")
@RequiredArgsConstructor
public class OrderController {

    private final OrderProcessingService orderProcessingService;

    @PostMapping
    public ResponseEntity<Void> handlePubSubPush(@Valid @RequestBody InputMessage inputMessage) {
        log.debug("Received push notification for message ID: {}", inputMessage.getMessageId());
        orderProcessingService.processOrder(inputMessage);

        return ResponseEntity.ok().build();
    }
}