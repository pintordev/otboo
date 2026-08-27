package com.sprint.mission.otboo.domain.weathernotification.notification.exception;

import java.util.List;
import java.util.UUID;

// 컨트롤러까지 안 올라가고 Kafka 컨슈머 안에서만 소비되는 내부 예외라 OtbooException을
// 상속하지 않는다(5번 컨벤션) — 이 예외가 KafkaConfig의 공용 에러 핸들러(재시도 2회 + DLT)에
// 전파돼야 아직 전달 안 된 알림이 재시도된다.
public class NotificationSseDeliveryFailedException extends RuntimeException {

  private NotificationSseDeliveryFailedException(List<UUID> failedNotificationIds) {
    super("SSE 전달 실패: notificationIds=" + failedNotificationIds);
  }

  public static NotificationSseDeliveryFailedException of(List<UUID> failedNotificationIds) {
    return new NotificationSseDeliveryFailedException(failedNotificationIds);
  }
}