package com.sprint.mission.otboo.domain.weathernotification.notification.repository.querydsl;

import com.sprint.mission.otboo.domain.weathernotification.notification.dto.NotificationDto;
import com.sprint.mission.otboo.domain.weathernotification.notification.dto.NotificationListParams;
import com.sprint.mission.otboo.global.dto.CursorPageResponse;
import java.util.UUID;

public interface NotificationCustomRepository {

  CursorPageResponse<NotificationDto> findNotifications(UUID receiverId, NotificationListParams params);
}