package com.sprint.mission.otboo.global.sse;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.stream.Stream;
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

    public List<SseMessage> findAllAfter(UUID lastEventId, UUID userId) {
        if (lastEventId == null) {
            return List.of();
        }
        boolean found = messages.containsKey(lastEventId);
        Stream<UUID> ids = eventIdQueue.stream();
        if (found) {
            ids = ids.dropWhile(id -> !id.equals(lastEventId)).skip(1);
        }
        return ids.map(messages::get)
                .filter(Objects::nonNull)
                .filter(message -> message.isTargetedTo(userId))
                .toList();
    }

    public UUID getLatestEventId() {
        return eventIdQueue.peekLast();
    }
}