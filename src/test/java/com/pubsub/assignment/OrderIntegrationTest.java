package com.pubsub.assignment;

import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.pubsub.v1.*;
import com.google.protobuf.ByteString;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PubsubMessage;
import com.google.pubsub.v1.PushConfig;
import com.google.pubsub.v1.TopicName;
import com.pubsub.assignment.model.json.ErrorResponse;
import com.pubsub.assignment.model.json.FailedMessage;
import com.pubsub.assignment.model.json.InputMessage;
import com.pubsub.assignment.publisher.OrderPublisher;
import com.pubsub.assignment.service.IdempotencyService;
import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.testcontainers.containers.PubSubEmulatorContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeast;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderIntegrationTest {

    private static final String PROJECT_ID = "test-project";
    private static final String TOPIC_ID = "orders.transformed";
    private static final String FAILED_TOPIC_ID = "orders.failed";
    private static final String INPUT_TOPIC_ID = "orders.ubl";
    private static final String INPUT_SUBSCRIPTION_ID = "orders.ubl.received";
    private static final String SUBSCRIPTION_ID = "test-subscription";
    private static final String FAILED_SUBSCRIPTION_ID = "test-failed-subscription";

    @Container
    private static final PubSubEmulatorContainer PUB_SUB_EMULATOR =
            new PubSubEmulatorContainer(DockerImageName
                    .parse("gcr.io/google.com/cloudsdktool/google-cloud-cli:441.0.0-emulators"));

    @Autowired
    private TestRestTemplate restTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoSpyBean
    private OrderPublisher orderPublisher;

    @MockitoSpyBean
    private IdempotencyService idempotencyService;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.gcp.project-id", () -> PROJECT_ID);
        registry.add("spring.cloud.gcp.pubsub.emulator-host", PUB_SUB_EMULATOR::getEmulatorEndpoint);
        registry.add("app.pubsub.input-subscription", () -> INPUT_SUBSCRIPTION_ID);
        registry.add("app.pubsub.output-topic", () -> TOPIC_ID);
        registry.add("app.pubsub.failed-topic", () -> FAILED_TOPIC_ID);
        registry.add("app.pubsub.retry.backoff-ms", () -> 10);
    }

    @BeforeAll
    static void setupPubSub() throws IOException {
        ManagedChannel channel = ManagedChannelBuilder
                .forTarget("dns:///" + PUB_SUB_EMULATOR.getEmulatorEndpoint())
                .usePlaintext()
                .build();
        TransportChannelProvider channelProvider = FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel));
        NoCredentialsProvider credentialsProvider = NoCredentialsProvider.create();

        try (TopicAdminClient topicAdminClient = TopicAdminClient.create(
                TopicAdminSettings.newBuilder()
                        .setTransportChannelProvider(channelProvider)
                        .setCredentialsProvider(credentialsProvider)
                        .build())) {
            topicAdminClient.createTopic(ProjectTopicName.of(PROJECT_ID, TOPIC_ID));
            topicAdminClient.createTopic(ProjectTopicName.of(PROJECT_ID, FAILED_TOPIC_ID));
            topicAdminClient.createTopic(ProjectTopicName.of(PROJECT_ID, INPUT_TOPIC_ID));
        }

        try (SubscriptionAdminClient subscriptionAdminClient = SubscriptionAdminClient.create(
                SubscriptionAdminSettings.newBuilder()
                        .setTransportChannelProvider(channelProvider)
                        .setCredentialsProvider(credentialsProvider)
                        .build())) {
            subscriptionAdminClient.createSubscription(
                    ProjectSubscriptionName.of(PROJECT_ID, SUBSCRIPTION_ID),
                    ProjectTopicName.of(PROJECT_ID, TOPIC_ID),
                    PushConfig.getDefaultInstance(),
                    10);

            subscriptionAdminClient.createSubscription(
                    ProjectSubscriptionName.of(PROJECT_ID, FAILED_SUBSCRIPTION_ID),
                    ProjectTopicName.of(PROJECT_ID, FAILED_TOPIC_ID),
                    PushConfig.getDefaultInstance(),
                    10);

            subscriptionAdminClient.createSubscription(
                    ProjectSubscriptionName.of(PROJECT_ID, INPUT_SUBSCRIPTION_ID),
                    ProjectTopicName.of(PROJECT_ID, INPUT_TOPIC_ID),
                    PushConfig.getDefaultInstance(),
                    10);
        }

        channel.shutdown();
    }

    @Test
    void shouldProcessOrderAndPublishToPubSub() throws Exception {
        String validXml = "<Order><ID>99999</ID></Order>";
        String base64Xml = Base64.getEncoder().encodeToString(validXml.getBytes());

        InputMessage request = new InputMessage();
        request.setMessageId("msg-integration-1");
        request.setTimestamp("2026-08-25T12:00:00Z");
        request.setDocument(base64Xml);

        ArrayBlockingQueue<String> receivedMessages = new ArrayBlockingQueue<>(1);
        ManagedChannel channel = ManagedChannelBuilder
                .forTarget("dns:///" + PUB_SUB_EMULATOR.getEmulatorEndpoint())
                .usePlaintext()
                .build();

        MessageReceiver receiver = (message, consumer) -> {
            receivedMessages.add(message.getData().toStringUtf8());
            consumer.ack();
        };

        Subscriber subscriber = Subscriber.newBuilder(
                        ProjectSubscriptionName.of(PROJECT_ID, SUBSCRIPTION_ID),
                        receiver)
                .setChannelProvider(FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel)))
                .setCredentialsProvider(NoCredentialsProvider.create())
                .build();

        subscriber.startAsync().awaitRunning();

        try {
            ResponseEntity<Void> response = restTemplate.postForEntity("/api/orders", request, Void.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

            String publishedPayload = receivedMessages.poll(5, TimeUnit.SECONDS);
            assertThat(publishedPayload).isNotNull();
            assertThat(publishedPayload).contains("\"orderId\":\"99999\"");
        } finally {
            subscriber.stopAsync();
        }
    }

    @Test
    void shouldPublishToDlqTopicWhenProcessingFails() throws Exception {
        InputMessage request = new InputMessage();
        request.setMessageId("msg-dlq-test");
        request.setTimestamp("2026-08-25T12:00:00Z");
        request.setDocument("InvalidBase64Content!!!");

        ArrayBlockingQueue<String> dlqMessages = new ArrayBlockingQueue<>(1);
        ManagedChannel channel = ManagedChannelBuilder
                .forTarget("dns:///" + PUB_SUB_EMULATOR.getEmulatorEndpoint())
                .usePlaintext()
                .build();

        MessageReceiver receiver = (message, consumer) -> {
            dlqMessages.add(message.getData().toStringUtf8());
            consumer.ack();
        };

        Subscriber subscriber = Subscriber.newBuilder(
                        ProjectSubscriptionName.of(PROJECT_ID, FAILED_SUBSCRIPTION_ID),
                        receiver)
                .setChannelProvider(FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel)))
                .setCredentialsProvider(NoCredentialsProvider.create())
                .build();

        subscriber.startAsync().awaitRunning();

        try {
            ResponseEntity<ErrorResponse> response = restTemplate.postForEntity("/api/orders", request, ErrorResponse.class);

            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

            String dlqPayload = dlqMessages.poll(5, TimeUnit.SECONDS);
            assertThat(dlqPayload).isNotNull();
            assertThat(dlqPayload).contains("\"messageId\":\"msg-dlq-test\"");
            assertThat(dlqPayload).contains("\"reason\":\"Document is not a valid Base64 string.\"");
            assertThat(dlqPayload).contains("\"rawDocument\":\"InvalidBase64Content!!!\"");
        } finally {
            subscriber.stopAsync();
        }
    }

    @Test
    void shouldConsumeFromSubscriptionAndPublishTransformed() throws Exception {
        String validXml = "<Order><ID>77777</ID></Order>";
        String base64Xml = Base64.getEncoder().encodeToString(validXml.getBytes());

        InputMessage request = new InputMessage();
        request.setMessageId("msg-subscription-1");
        request.setTimestamp("2026-08-25T12:00:00Z");
        request.setDocument(base64Xml);

        ArrayBlockingQueue<String> receivedMessages = new ArrayBlockingQueue<>(1);
        ManagedChannel channel = ManagedChannelBuilder
                .forTarget("dns:///" + PUB_SUB_EMULATOR.getEmulatorEndpoint())
                .usePlaintext()
                .build();

        MessageReceiver receiver = (message, consumer) -> {
            receivedMessages.add(message.getData().toStringUtf8());
            consumer.ack();
        };

        Subscriber subscriber = Subscriber.newBuilder(
                        ProjectSubscriptionName.of(PROJECT_ID, SUBSCRIPTION_ID),
                        receiver)
                .setChannelProvider(FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel)))
                .setCredentialsProvider(NoCredentialsProvider.create())
                .build();

        subscriber.startAsync().awaitRunning();

        Publisher publisher = Publisher.newBuilder(TopicName.of(PROJECT_ID, INPUT_TOPIC_ID))
                .setChannelProvider(FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel)))
                .setCredentialsProvider(NoCredentialsProvider.create())
                .build();

        try {
            String payload = objectMapper.writeValueAsString(request);
            publisher.publish(PubsubMessage.newBuilder()
                    .setData(ByteString.copyFromUtf8(payload))
                    .build());

            String publishedPayload = receivedMessages.poll(10, TimeUnit.SECONDS);
            assertThat(publishedPayload).isNotNull();
            assertThat(publishedPayload).contains("\"messageId\":\"msg-subscription-1\"");
            assertThat(publishedPayload).contains("\"orderId\":\"77777\"");
        } finally {
            publisher.shutdown();
            subscriber.stopAsync();
        }
    }

    @Test
    void shouldRouteMalformedMessageFromSubscriptionToDlqExactlyOnce() throws Exception {
        String malformedMessageId = "msg-consumer-malformed";

        InputMessage request = new InputMessage();
        request.setMessageId(malformedMessageId);
        request.setTimestamp("2026-08-25T12:00:00Z");
        request.setDocument("InvalidBase64Content!!!");

        ArrayBlockingQueue<String> dlqMessages = new ArrayBlockingQueue<>(10);
        ManagedChannel channel = ManagedChannelBuilder
                .forTarget("dns:///" + PUB_SUB_EMULATOR.getEmulatorEndpoint())
                .usePlaintext()
                .build();

        MessageReceiver receiver = (message, consumer) -> {
            dlqMessages.add(message.getData().toStringUtf8());
            consumer.ack();
        };

        Subscriber subscriber = Subscriber.newBuilder(
                        ProjectSubscriptionName.of(PROJECT_ID, FAILED_SUBSCRIPTION_ID),
                        receiver)
                .setChannelProvider(FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel)))
                .setCredentialsProvider(NoCredentialsProvider.create())
                .build();

        subscriber.startAsync().awaitRunning();

        Publisher publisher = Publisher.newBuilder(TopicName.of(PROJECT_ID, INPUT_TOPIC_ID))
                .setChannelProvider(FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel)))
                .setCredentialsProvider(NoCredentialsProvider.create())
                .build();

        try {
            String payload = objectMapper.writeValueAsString(request);
            publisher.publish(PubsubMessage.newBuilder()
                    .setData(ByteString.copyFromUtf8(payload))
                    .build());

            String dlqPayload = pollForMessageId(dlqMessages, malformedMessageId, 10);
            assertThat(dlqPayload).isNotNull();
            assertThat(dlqPayload).contains("\"reason\":\"Document is not a valid Base64 string.\"");

            String duplicate = pollForMessageId(dlqMessages, malformedMessageId, 3);
            assertThat(duplicate).isNull();
        } finally {
            publisher.shutdown();
            subscriber.stopAsync();
        }
    }

    @Test
    void shouldNackAndRedeliverWhenDlqRoutingFails() throws Exception {
        String messageId = "msg-dlq-nack";

        InputMessage request = new InputMessage();
        request.setMessageId(messageId);
        request.setTimestamp("2026-08-25T12:00:00Z");
        request.setDocument("InvalidBase64Content!!!");

        // Simulate DLQ publishing failing for the first two attempts, then succeeding.
        // Each failure raises DlqRoutingException -> consumer nack() -> redelivery.
        AtomicInteger dlqAttempts = new AtomicInteger();
        doAnswer(invocation -> {
            if (dlqAttempts.incrementAndGet() <= 2) {
                return CompletableFuture.failedFuture(new RuntimeException("Simulated DLQ publish failure"));
            }
            return invocation.callRealMethod();
        }).when(orderPublisher).publishToDlq(any(FailedMessage.class));

        ArrayBlockingQueue<String> dlqMessages = new ArrayBlockingQueue<>(10);
        ManagedChannel channel = ManagedChannelBuilder
                .forTarget("dns:///" + PUB_SUB_EMULATOR.getEmulatorEndpoint())
                .usePlaintext()
                .build();

        MessageReceiver receiver = (message, consumer) -> {
            dlqMessages.add(message.getData().toStringUtf8());
            consumer.ack();
        };

        Subscriber subscriber = Subscriber.newBuilder(
                        ProjectSubscriptionName.of(PROJECT_ID, FAILED_SUBSCRIPTION_ID),
                        receiver)
                .setChannelProvider(FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel)))
                .setCredentialsProvider(NoCredentialsProvider.create())
                .build();

        subscriber.startAsync().awaitRunning();

        Publisher publisher = Publisher.newBuilder(TopicName.of(PROJECT_ID, INPUT_TOPIC_ID))
                .setChannelProvider(FixedTransportChannelProvider.create(GrpcTransportChannel.create(channel)))
                .setCredentialsProvider(NoCredentialsProvider.create())
                .build();

        try {
            String payload = objectMapper.writeValueAsString(request);
            publisher.publish(PubsubMessage.newBuilder()
                    .setData(ByteString.copyFromUtf8(payload))
                    .build());

            // The DLQ message only materialises on the third attempt (the first real publish),
            // which is reachable only via nack()-driven redelivery of the input message.
            String dlqPayload = pollForMessageId(dlqMessages, messageId, 20);
            assertThat(dlqPayload).isNotNull();
            assertThat(dlqPayload).contains("\"reason\":\"Document is not a valid Base64 string.\"");

            // Two failed DLQ routings => two DlqRoutingExceptions => two nack()s + redeliveries,
            // and idempotency must be unregistered each time so the message can be reprocessed.
            verify(orderPublisher, atLeast(3)).publishToDlq(any(FailedMessage.class));
            verify(idempotencyService, atLeast(2)).unregister(messageId);
        } finally {
            publisher.shutdown();
            subscriber.stopAsync();
        }
    }

    private static String pollForMessageId(ArrayBlockingQueue<String> queue, String messageId, long timeoutSeconds)
            throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(timeoutSeconds);
        while (System.nanoTime() < deadline) {
            long remaining = deadline - System.nanoTime();
            String message = queue.poll(remaining, TimeUnit.NANOSECONDS);
            if (message == null) {
                return null;
            }
            if (message.contains("\"messageId\":\"" + messageId + "\"")) {
                return message;
            }
        }
        return null;
    }
}