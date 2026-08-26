package com.pubsub.assignment.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "app.pubsub.retry")
public class RetryProperties {
    private int maxAttempts = 3;
    private long backoffMs = 1000;
}