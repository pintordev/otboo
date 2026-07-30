package com.sprint.mission.otboo.domain.weathernotification.notification.controller;

import com.sprint.mission.otboo.domain.weathernotification.notification.controller.api.NotificationApi;
import com.sprint.mission.otboo.domain.weathernotification.notification.dto.NotificationDto;
import com.sprint.mission.otboo.domain.weathernotification.notification.dto.NotificationListParams;
import com.sprint.mission.otboo.domain.weathernotification.notification.service.NotificationService;
import com.sprint.mission.otboo.global.dto.CursorPageResponse;
import com.sprint.mission.otboo.global.security.jwt.filter.CurrentUser;
import com.sprint.mission.otboo.global.security.jwt.filter.UserPrincipal;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@RestController
public class NotificationController implements NotificationApi {

  private final NotificationService notificationService;

  @GetMapping
  @Override
  public ResponseEntity<CursorPageResponse<NotificationDto>> getNotifications(
      @CurrentUser UserPrincipal principal,
      @Valid @ModelAttribute NotificationListParams params) {
    return ResponseEntity.ok(notificationService.getNotifications(principal.userId(), params));
  }
}