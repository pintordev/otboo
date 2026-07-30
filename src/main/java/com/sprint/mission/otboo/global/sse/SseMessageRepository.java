package com.sprint.mission.otboo.global.sse;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import org.springframework.stereotype.Repository;

@Repository
public class SseMessageRepository {

    private final ConcurrentLinkedDeque<UUID> eventIdQueue = new ConcurrentLinkedDeque<>();
    private final Map<UUID, SseMessage> messages = new ConcurrentHashMap<>();

    public UUID save(SseMessage message) {
        messages.put(message.id(), message);
        eventIdQueue.addLast(message.id());
        return message.id();
    }

    public UUID getLatestEventId() {
        return eventIdQueue.peekLast();
    }
}