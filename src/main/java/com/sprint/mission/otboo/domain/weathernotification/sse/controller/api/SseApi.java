package com.sprint.mission.otboo.domain.weathernotification.sse.controller.api;

import com.sprint.mission.otboo.global.security.jwt.filter.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "sse-controller")
public interface SseApi {

  @Operation(summary = "SSE 구독", description = "알림 SSE 스트림을 구독합니다.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "OK")
  })
  SseEmitter subscribe(UserPrincipal principal, UUID lastEventId);
}