package com.sprint.mission.otboo.domain.weathernotification.sse.controller.api;

import com.sprint.mission.otboo.security.details.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Tag(name = "SSE 알림 구독", description = "SSE 알림 구독 API")
public interface SseApi {

  @Operation(summary = "SSE 구독", description = "알림 SSE 스트림을 구독합니다.")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "OK")
  })
  SseEmitter subscribe(
      @Parameter(hidden = true) UserPrincipal principal,
      @Parameter(description = "마지막으로 수신한 이벤트의 id. 최초 연결 시 생략") UUID lastEventId);
}