# UBL 2.1 XML to JSON Order Transformation Service

This service consumes Base64-encoded UBL 2.1 Order XML payload events via Google Cloud Pub/Sub, validates and parses the XML structure, maps it into a standardized JSON order format, and publishes the result to a downstream Pub/Sub topic.

## Architecture

The application is built with Spring Boot 3.4.2 and Java 21, utilizing an asynchronous non-blocking flow (`CompletableFuture`) for message handling:

*   **REST Endpoint (`/api/orders`)**: Accepts an order message directly via HTTP POST for synchronous/manual submission.
*   **Pub/Sub Consumer**: A Spring Integration `PubSubInboundChannelAdapter` pulls messages from the `app.pubsub.input-subscription` subscription and routes them through the same processing pipeline.
*   **Idempotency**: Utilizes an in-memory Least Recently Used (LRU) cache (via `IdempotencyService`) to track and ignore duplicate `messageId`s, preventing redundant processing and duplicate downstream messages.
*   **OrderTransformationService**: Decodes Base64 payloads and parses UBL 2.1 XML using namespace-aware JAXB bindings (`@XmlSchema`/`@XmlElement(namespace = ...)`) matching the official `Order-2`, `cbc`, and `cac` UBL namespaces.
*   **OrderMapper**: MapStruct mapper that converts UBL XML objects to the output JSON domain model.
*   **OrderPublisher**: Asynchronously publishes transformed messages to Google Cloud Pub/Sub using `PubSubTemplate`.
*   **Retry Mechanism**: Configurable asynchronous retries (`app.pubsub.retry.max-attempts`, `app.pubsub.retry.backoff-ms`) for transient errors (e.g., publishing failures). Non-retriable business errors (validation, parsing) bypass retries and are routed immediately to the DLQ.
*   **Dead Letter Queue (DLQ)**: Failed messages resulting from invalid Base64, XML parsing issues, missing required fields, or exhausted publishing retries are automatically routed to the `orders.failed` topic.
*   **GlobalExceptionHandler**: Converts application exceptions (`InvalidBase64Exception`, `InvalidXmlException`, `MissingFieldException`) as well as unreadable/missing request bodies (`HttpMessageNotReadableException`) into standard `400 Bad Request` error responses.
*   **Observability**: Implements structured JSON logging (ECS format) with MDC correlation (including `messageId` and GCP `traceId`), and tracks processing performance and latency using Micrometer/Actuator (`order.processing.duration`, `order.message.age`).

## Prerequisites

*   Java 21 JDK
*   Docker & Docker Compose

## Containerization

The `Dockerfile` uses a multi-stage build following production best practices:

*   **Layer Caching**: Dependency-related files (`gradlew`, `settings.gradle`, `build.gradle`, `gradle/`) are copied and resolved (`./gradlew dependencies`) *before* application source is copied. Source-only changes therefore reuse the cached dependency layer instead of re-downloading them.
*   **Non-Root User**: The runtime image creates and runs as a dedicated unprivileged `spring` system user/group rather than root.
*   **Explicit `${PORT}` Resolution**: The `ENTRYPOINT` uses shell form (`sh -c exec java -Dserver.port=${PORT} -jar app.jar`) so the `PORT` environment variable is reliably expanded by the shell before the JVM starts, and `exec` ensures `java` becomes PID 1 to receive `SIGTERM` directly for graceful shutdown.

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

*   **Message Age Latency**: The `order.message.age` Micrometer timer measures the elapsed time between `InputMessage.timestamp` (when the message was sent) and when it was received for processing, giving end-to-end pipeline latency independent of processing duration. Messages with a missing or unparseable `timestamp` are logged as a warning and skipped for this metric, but still processed normally.

```bash
curl -s http://localhost:8080/actuator/metrics/order.message.age
```

*   **Health & Readiness Probes**: `management.endpoint.health.probes.enabled=true` exposes `/actuator/health`, plus `/actuator/health/liveness` and `/actuator/health/readiness` group endpoints (backed by `livenessstate`/`readinessstate` indicators) suitable for Cloud Run startup/liveness/readiness probes. `management.health.pubsub` is explicitly disabled (`management.health.pubsub.enabled=false`) because `spring-cloud-gcp-starter-pubsub` 4.1.1's `PubSubHealthIndicatorAutoConfiguration` is binary-incompatible with Spring Boot 3.4.2's Actuator (`NoSuchMethodError` on `CompositeHealthContributorConfiguration`'s no-arg constructor), which would crash the application context on startup if enabled.

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

### Missing or Unreadable Request Body (400 Bad Request)

An empty, missing, or malformed JSON request body is handled explicitly instead of surfacing as a 500 error:

```json
{
  "error": "ValidationError",
  "message": "Request body is missing or malformed.",
  "messageId": "unknown"
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