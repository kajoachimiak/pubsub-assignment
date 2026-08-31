# UBL 2.1 XML to JSON Order Transformation Service

This service consumes Base64-encoded UBL 2.1 Order XML payload events via Google Cloud Pub/Sub, validates and parses the XML structure, maps it into a standardized JSON order format, and publishes the result to a downstream Pub/Sub topic.

## Architecture

The application is built with Spring Boot 3.4.2 and Java 21, utilizing an asynchronous non-blocking flow (`CompletableFuture`) for message handling:

*   **REST Endpoint (`/api/orders`)**: Receives push notifications from Pub/Sub.
*   **Idempotency**: Utilizes an in-memory Least Recently Used (LRU) cache (via `IdempotencyService`) to track and ignore duplicate `messageId`s, preventing redundant processing and duplicate downstream messages.
*   **OrderTransformationService**: Decodes Base64 payloads and parses UBL 2.1 XML using namespace-aware JAXB bindings (`@XmlSchema`/`@XmlElement(namespace = ...)`) matching the official `Order-2`, `cbc`, and `cac` UBL namespaces.
*   **OrderMapper**: MapStruct mapper that converts UBL XML objects to the output JSON domain model.
*   **OrderPublisher**: Asynchronously publishes transformed messages to Google Cloud Pub/Sub using `PubSubTemplate`.
*   **Retry Mechanism**: Configurable asynchronous retries (`app.pubsub.retry.max-attempts`, `app.pubsub.retry.backoff-ms`) for transient errors (e.g., publishing failures). Non-retriable business errors (validation, parsing) bypass retries and are routed immediately to the DLQ.
*   **Dead Letter Queue (DLQ)**: Failed messages resulting from invalid Base64, XML parsing issues, missing required fields, or exhausted publishing retries are automatically routed to the `orders.failed` topic.
*   **GlobalExceptionHandler**: Converts application exceptions (`InvalidBase64Exception`, `InvalidXmlException`, `MissingFieldException`) into standard error responses.
*   **Observability**: Implements structured JSON logging (ECS format) with MDC correlation (including `messageId` and GCP `traceId`), and tracks processing performance using Micrometer/Actuator (`order.processing.duration`).

## Prerequisites

*   Java 21 JDK
*   Docker & Docker Compose

## Getting Started

### Running with Docker Compose

The environment includes the Spring Boot application and a Google Cloud Pub/Sub emulator.

```bash
docker compose up --build
```

The service will start on http://localhost:8080, and the Pub/Sub emulator will run on port 8085. An initialization script automatically creates the required Pub/Sub topics (`orders.transformed` and `orders.failed`).

### Running Locally

Start only the Pub/Sub emulator:

```bash
docker compose up pubsub-emulator pubsub-init
```

Run the Spring Boot application:

```bash
./gradlew bootRun
```

## Observability

The application includes built-in observability features to monitor and debug processing:

*   **Structured Logging & Correlation**: Console logs are output in ECS-compliant JSON format. Each log entry within the processing flow is automatically correlated with the `messageId` and `traceId` using Mapped Diagnostic Context (MDC).
*   **Metrics**: Processing duration and attempt counts are tracked using Micrometer. You can view the `order.processing.duration` metric (tagged by `status="success"` or `status="error"`) via the Spring Boot Actuator endpoint:

```bash
curl -s http://localhost:8080/actuator/metrics/order.processing.duration
```

## How to Test

### Publish Test Message via cURL

You can send the included test message from the `examples` directory:

```bash
curl -i -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d @examples/sample-message.json
```

### Generate Custom Test Payload

To send custom XML documents, encode the XML in Base64 and structure the payload:

```bash
cat <<EOF> examples/custom-message.json
{
  "messageId": "msg-999",
  "timestamp": "2026-08-25T12:00:00Z",
  "document": "$(base64 -w 0 src/test/resources/ubl-example.xml)"
}
EOF

curl -i -X POST http://localhost:8080/api/orders \
  -H "Content-Type: application/json" \
  -d @examples/custom-message.json
```

## Sample Messages & Responses

### Sample Input Payload

```json
{
  "messageId": "7a1e78c9",
  "timestamp": "2026-07-27T12:00:00Z",
  "document": "PD94bWwgdmVyc2lvbj0iMS4wIiBlbmNvZGluZz0iVVRGLTgiIHN0YW5kYWxvbmU9InllcyI/Pg..."
}
```

### Successful Output Message (`orders.transformed`)

```json
{
  "messageId": "7a1e78c9",
  "transformedAt": "2026-08-25T12:01:00Z",
  "order": {
    "orderId": "12347",
    "externalOrganizationId": "1234567",
    "reference": "test-order",
    "orderDate": "2025-01-01",
    "lines": [
      {
        "lineId": 1,
        "itemId": "100100",
        "quantity": 1,
        "unitOfMeasure": "EA",
        "comment": "Optional OrderLine Note"
      }
    ]
  }
}
```

### Dead Letter Queue Message (`orders.failed`)

```json
{
  "messageId": "7a1e78c9",
  "failedAt": "2026-08-25T12:01:00Z",
  "reason": "Document is not a valid Base64 string.",
  "rawDocument": "InvalidBase64Content!!!"
}
```

## Error Responses

### Invalid Base64 (400 Bad Request)

```json
{
  "error": "InvalidBase64",
  "message": "Document is not a valid Base64 string.",
  "messageId": "7a1e78c9"
}
```

### Invalid XML (400 Bad Request)

```json
{
  "error": "InvalidXml",
  "message": "Message could not be parsed from XML to JSON.",
  "messageId": "7a1e78c9"
}
```

### Missing Required Fields (400 Bad Request)

```json
{
  "error": "ValidationError",
  "message": "Order ID is required.",
  "messageId": "7a1e78c9"
}
```

### Server Error (500 Internal Server Error)

```json
{
  "error": "ServerError",
  "message": "An unexpected error occurred.",
  "messageId": "unknown"
}
```

## Running Tests

Execute unit and integration tests (which utilize Testcontainers for the GCP Pub/Sub emulator):

```bash
./gradlew test
```