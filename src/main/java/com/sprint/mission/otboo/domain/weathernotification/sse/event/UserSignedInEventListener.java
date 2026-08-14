package com.sprint.mission.otboo.domain.weathernotification.sse.event;

import com.sprint.mission.otboo.domain.weathernotification.sse.service.SseService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class UserSignedInEventListener {

  private final SseService sseService;

  @Async("sseDisconnectExecutor")
  @EventListener
  public void on(UserSignedInEvent event) {
    sseService.disconnect(event.userId());
  }
}