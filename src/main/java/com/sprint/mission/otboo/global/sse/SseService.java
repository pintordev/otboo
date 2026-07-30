package com.sprint.mission.otboo.global.sse;

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Slf4j
@RequiredArgsConstructor
@Service
public class SseService {

    private static final long TIMEOUT = Duration.ofMinutes(30).toMillis();
    private static final String PING_EVENT_NAME = "ping";

    private final SseEmitterRepository sseEmitterRepository;
    private final SseMessageRepository sseMessageRepository;

    public SseEmitter connect(UUID userId, UUID lastEventId) {
        SseEmitter emitter = new SseEmitter(TIMEOUT);
        emitter.onCompletion(() -> sseEmitterRepository.remove(userId, emitter));
        emitter.onTimeout(() -> sseEmitterRepository.remove(userId, emitter));
        emitter.onError(e -> sseEmitterRepository.remove(userId, emitter));

        UUID connectionTimeLatestId = sseMessageRepository.getLatestEventId();
        sseEmitterRepository.save(userId, emitter);
        if (!ping(emitter)) {
            return emitter;
        }

        List<SseMessage> missed = sseMessageRepository.findAllAfter(lastEventId, userId);
        for (SseMessage message : missed) {
            if (!sendToEmitter(emitter, message)) {
                break;
            }
            if (message.id().equals(connectionTimeLatestId)) {
                break;
            }
        }
        return emitter;
    }

    private boolean sendToEmitter(SseEmitter emitter, SseMessage message) {
        try {
            emitter.send(SseEmitter.event()
                    .id(message.id().toString())
                    .name(message.eventName())
                    .data(message.data()));
            return true;
        } catch (IOException | IllegalStateException e) {
            log.warn("sse send fail, closing emitter: messageId={}", message.id(), e);
            emitter.completeWithError(e);
            return false;
        }
    }

    private boolean ping(SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name(PING_EVENT_NAME).data(""));
            return true;
        } catch (IOException | IllegalStateException e) {
            emitter.completeWithError(e);
            return false;
        }
    }
}