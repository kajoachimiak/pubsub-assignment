package com.pubsub.assignment.service;

import com.fasterxml.jackson.dataformat.xml.XmlMapper;
import com.pubsub.assignment.exception.InvalidBase64Exception;
import com.pubsub.assignment.exception.InvalidXmlException;
import com.pubsub.assignment.exception.MissingFieldException;
import com.pubsub.assignment.mapper.OrderMapper;
import com.pubsub.assignment.model.json.OrderJson;
import com.pubsub.assignment.model.xml.OrderXml;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Base64;

@Service
@RequiredArgsConstructor
public class OrderTransformationService {

    private final OrderMapper orderMapper;
    private final XmlMapper xmlMapper = new XmlMapper();

    public OrderJson transform(String base64Document, String messageId) {
        byte[] decodedXmlBytes;

        try {
            decodedXmlBytes = Base64.getDecoder().decode(base64Document);
        } catch (IllegalArgumentException e) {
            throw new InvalidBase64Exception("Document is not a valid Base64 string.", messageId);
        }

        OrderJson orderJson;
        try {
            OrderXml orderXml = xmlMapper.readValue(decodedXmlBytes, OrderXml.class);
            orderJson = orderMapper.toOrderJson(orderXml);
        } catch (Exception e) {
            throw new InvalidXmlException("Message could not be parsed from XML to JSON.", messageId, e);
        }

        if (orderJson.getOrderId() == null || orderJson.getOrderId().isBlank()) {
            throw new MissingFieldException("Order ID is required.", messageId);
        }

        return orderJson;
    }
}