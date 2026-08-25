package com.pubsub.assignment;

import com.google.api.gax.core.NoCredentialsProvider;
import com.google.api.gax.grpc.GrpcTransportChannel;
import com.google.api.gax.rpc.FixedTransportChannelProvider;
import com.google.api.gax.rpc.TransportChannelProvider;
import com.google.cloud.pubsub.v1.*;
import com.google.pubsub.v1.ProjectSubscriptionName;
import com.google.pubsub.v1.ProjectTopicName;
import com.google.pubsub.v1.PushConfig;
import com.pubsub.assignment.model.json.ErrorResponse;
import com.pubsub.assignment.model.json.InputMessage;
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
import org.testcontainers.containers.PubSubEmulatorContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.io.IOException;
import java.util.Base64;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class OrderIntegrationTest {

    private static final String PROJECT_ID = "test-project";
    private static final String TOPIC_ID = "orders.transformed";
    private static final String SUBSCRIPTION_ID = "test-subscription";

    @Container
    private static final PubSubEmulatorContainer PUB_SUB_EMULATOR =
            new PubSubEmulatorContainer(DockerImageName
                    .parse("gcr.io/google.com/cloudsdktool/google-cloud-cli:441.0.0-emulators"));

    @Autowired
    private TestRestTemplate restTemplate;

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.gcp.project-id", () -> PROJECT_ID);
        registry.add("spring.cloud.gcp.pubsub.emulator-host", PUB_SUB_EMULATOR::getEmulatorEndpoint);
        registry.add("app.pubsub.output-topic", () -> TOPIC_ID);
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
    void shouldReturnBadRequestWhenBase64IsInvalid() {
        InputMessage request = new InputMessage();
        request.setMessageId("msg-invalid-base64");
        request.setTimestamp("2026-08-25T12:00:00Z");
        request.setDocument("NotValidBase64!!!");

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity("/api/orders", request, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("InvalidBase64");
        assertThat(response.getBody().message()).isEqualTo("Document is not a valid Base64 string.");
        assertThat(response.getBody().messageId()).isEqualTo("msg-invalid-base64");
    }

    @Test
    void shouldReturnBadRequestWhenXmlIsMalformed() {
        String malformedXml = "<Order><ID>99999</ID>";
        String base64Xml = Base64.getEncoder().encodeToString(malformedXml.getBytes());

        InputMessage request = new InputMessage();
        request.setMessageId("msg-invalid-xml");
        request.setTimestamp("2026-08-25T12:00:00Z");
        request.setDocument(base64Xml);

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity("/api/orders", request, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("InvalidXml");
        assertThat(response.getBody().message()).isEqualTo("Message could not be parsed from XML to JSON.");
        assertThat(response.getBody().messageId()).isEqualTo("msg-invalid-xml");
    }

    @Test
    void shouldReturnBadRequestWhenOrderIdIsMissing() {
        String xmlWithoutId = "<Order></Order>";
        String base64Xml = Base64.getEncoder().encodeToString(xmlWithoutId.getBytes());

        InputMessage request = new InputMessage();
        request.setMessageId("msg-missing-field");
        request.setTimestamp("2026-08-25T12:00:00Z");
        request.setDocument(base64Xml);

        ResponseEntity<ErrorResponse> response = restTemplate.postForEntity("/api/orders", request, ErrorResponse.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().error()).isEqualTo("ValidationError");
        assertThat(response.getBody().message()).isEqualTo("Order ID is required.");
        assertThat(response.getBody().messageId()).isEqualTo("msg-missing-field");
    }
}