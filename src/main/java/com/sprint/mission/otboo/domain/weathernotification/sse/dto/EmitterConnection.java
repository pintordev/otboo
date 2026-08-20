package com.sprint.mission.otboo.domain.weathernotification.sse.dto;

import java.time.Instant;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public record EmitterConnection(SseEmitter emitter, Instant snapshotAt) {

}