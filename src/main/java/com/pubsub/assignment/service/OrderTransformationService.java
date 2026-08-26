package com.pubsub.assignment.service;

import com.pubsub.assignment.exception.InvalidBase64Exception;
import com.pubsub.assignment.exception.InvalidXmlException;
import com.pubsub.assignment.exception.MissingFieldException;
import com.pubsub.assignment.mapper.OrderMapper;
import com.pubsub.assignment.model.json.OrderJson;
import com.pubsub.assignment.model.xml.OrderXml;
import jakarta.xml.bind.JAXBContext;
import jakarta.xml.bind.JAXBException;
import jakarta.xml.bind.Unmarshaller;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamReader;
import javax.xml.stream.util.StreamReaderDelegate;
import java.io.ByteArrayInputStream;
import java.util.Base64;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderTransformationService {

    private final OrderMapper orderMapper;
    private static final JAXBContext JAXB_CONTEXT;

    static {
        try {
            JAXB_CONTEXT = JAXBContext.newInstance(OrderXml.class);
        } catch (JAXBException e) {
            throw new RuntimeException("Failed to initialize JAXB context", e);
        }
    }

    public OrderJson transform(String base64Document, String messageId) {
        byte[] decodedXmlBytes;
        try {
            decodedXmlBytes = Base64.getDecoder().decode(base64Document);
        } catch (IllegalArgumentException e) {
            throw new InvalidBase64Exception("Document is not a valid Base64 string.", messageId);
        }

        OrderJson orderJson;
        try {
            XMLInputFactory xif = XMLInputFactory.newFactory();
            xif.setProperty(XMLInputFactory.SUPPORT_DTD, false);
            xif.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            XMLStreamReader xsr = xif.createXMLStreamReader(new ByteArrayInputStream(decodedXmlBytes));

            XMLStreamReader noNsReader = new StreamReaderDelegate(xsr) {
                @Override
                public String getNamespaceURI() {
                    return "";
                }
            };

            Unmarshaller unmarshaller = JAXB_CONTEXT.createUnmarshaller();
            OrderXml orderXml = (OrderXml) unmarshaller.unmarshal(noNsReader);

            orderJson = orderMapper.toOrderJson(orderXml);
        } catch (Exception e) {
            log.error("Failed to parse XML", e);
            throw new InvalidXmlException("Message could not be parsed from XML to JSON.", messageId, e);
        }

        if (orderJson.getOrderId() == null || orderJson.getOrderId().isBlank()) {
            throw new MissingFieldException("Order ID is required.", messageId);
        }

        return orderJson;
    }
}