package com.sprint.mission.otboo.domain.weathernotification.notification.scheduler;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.sprint.mission.otboo.domain.weathernotification.notification.service.NotificationOutboxRelayService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationOutboxRelayScheduler")
class NotificationOutboxRelaySchedulerTest {

  @InjectMocks
  private NotificationOutboxRelayScheduler notificationOutboxRelayScheduler;

  @Mock
  private NotificationOutboxRelayService notificationOutboxRelayService;

  @Nested
  @DisplayName("relay")
  class Relay {

    @Test
    @DisplayName("릴레이_서비스만_호출하고_다른_로직은_없다")
    void 릴레이_서비스만_호출하고_다른_로직은_없다() {
      // when
      notificationOutboxRelayScheduler.relay();

      // then
      verify(notificationOutboxRelayService).relay();
      verifyNoMoreInteractions(notificationOutboxRelayService);
    }
  }
}