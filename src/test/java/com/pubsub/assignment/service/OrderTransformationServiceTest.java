package com.pubsub.assignment.service;

import com.pubsub.assignment.exception.InvalidBase64Exception;
import com.pubsub.assignment.exception.InvalidXmlException;
import com.pubsub.assignment.model.json.OrderJson;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.util.StreamUtils;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class OrderTransformationServiceTest {

    @Autowired
    private OrderTransformationService service;

    @Test
    void shouldSuccessfullyTransformValidBase64Xml() throws IOException {
        // Given
        String base64Document = loadAndEncodeFile("ubl-example.xml");

        // When
        OrderJson result = service.transform(base64Document, "testMessageId");

        // Then
        assertThat(result).isNotNull();
        assertThat(result.getOrderId()).isEqualTo("12347");
        assertThat(result.getOrderDate()).isEqualTo("2025-01-01");
        assertThat(result.getReference()).isEqualTo("test-order");
        assertThat(result.getExternalOrganizationId()).isEqualTo("1234567");

        assertThat(result.getLines()).hasSize(2);
        assertThat(result.getLines().get(0).getItemId()).isEqualTo("100100");
        assertThat(result.getLines().get(0).getUnitOfMeasure()).isEqualTo("EA");
    }

    @Test
    void shouldThrowInvalidBase64ExceptionWhenInputIsNotBase64() {
        // Given
        String invalidBase64 = "Invalid_Base64_!@#";

        // When & Then
        assertThatThrownBy(() -> service.transform(invalidBase64, "testMessageId"))
                .isInstanceOf(InvalidBase64Exception.class)
                .hasMessage("Document is not a valid Base64 string.");
    }

    @Test
    void shouldThrowInvalidXmlExceptionWhenXmlIsMalformed() {
        // Given
        String malformedXml = "<Order><cbc:ID>12347</cbc:ID>"; // Brak tagu zamykającego
        String base64Document = Base64.getEncoder().encodeToString(malformedXml.getBytes(StandardCharsets.UTF_8));

        // When & Then
        assertThatThrownBy(() -> service.transform(base64Document, "testMessageId"))
                .isInstanceOf(InvalidXmlException.class)
                .hasMessageContaining("Message could not be parsed from XML to JSON");
    }

    private String loadAndEncodeFile(String fileName) throws IOException {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(fileName)) {
            if (inputStream == null) {
                throw new IllegalArgumentException("File not found: " + fileName);
            }
            byte[] fileBytes = StreamUtils.copyToByteArray(inputStream);
            return Base64.getEncoder().encodeToString(fileBytes);
        }
    }
}