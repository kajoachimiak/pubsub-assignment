package com.pubsub.assignment.service;

import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

@Service
public class IdempotencyService {

    private static final int MAX_ENTRIES = 10000;

    // TODO: For production multi-instance environment replace this in-memory cache with a distributed cache like Redis or GCP Memorystore
    private final Set<String> processedMessages = Collections.synchronizedSet(
            Collections.newSetFromMap(new LinkedHashMap<>(16, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
                    return size() > MAX_ENTRIES;
                }
            })
    );

    public boolean register(String messageId) {
        return !processedMessages.add(messageId);
    }

    public void unregister(String messageId) {
        processedMessages.remove(messageId);
    }
}