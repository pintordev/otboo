package com.sprint.mission.otboo.domain.weathernotification.sse.controller;

import com.sprint.mission.otboo.domain.weathernotification.sse.controller.api.SseApi;
import com.sprint.mission.otboo.domain.weathernotification.sse.service.SseService;
import com.sprint.mission.otboo.security.details.CurrentUser;
import com.sprint.mission.otboo.security.details.UserPrincipal;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RequiredArgsConstructor
@RestController
public class SseController implements SseApi {

  private final SseService sseService;

  @Override
  @GetMapping(path = "/api/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
  public SseEmitter subscribe(
      @CurrentUser UserPrincipal principal,
      @RequestParam(value = "LastEventId", required = false) UUID lastEventId
  ) {
    return sseService.connect(principal.userId(), lastEventId);
  }
}