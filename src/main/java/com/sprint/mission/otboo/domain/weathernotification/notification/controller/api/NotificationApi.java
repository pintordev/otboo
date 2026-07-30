package com.sprint.mission.otboo.domain.weathernotification.notification.controller.api;

import com.sprint.mission.otboo.domain.weathernotification.notification.dto.NotificationDto;
import com.sprint.mission.otboo.domain.weathernotification.notification.dto.NotificationListParams;
import com.sprint.mission.otboo.global.dto.CursorPageResponse;
import com.sprint.mission.otboo.global.security.jwt.filter.UserPrincipal;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;

@Tag(name = "알림", description = "알림 API")
public interface NotificationApi {

  @Operation(summary = "알림 목록 조회", description = "알림 목록 조회 API")
  @ApiResponses({
      @ApiResponse(responseCode = "200", description = "알림 목록 조회 성공"),
      @ApiResponse(responseCode = "400", description = "알림 목록 조회 실패")
  })
  ResponseEntity<CursorPageResponse<NotificationDto>> getNotifications(
      UserPrincipal principal, NotificationListParams params);
}