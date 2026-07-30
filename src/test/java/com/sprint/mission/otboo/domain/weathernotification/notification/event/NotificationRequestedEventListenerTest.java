package com.sprint.mission.otboo.domain.weathernotification.notification.event;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.ConstructorPropertiesArbitraryIntrospector;
import com.navercorp.fixturemonkey.jakarta.validation.plugin.JakartaValidationPlugin;
import com.sprint.mission.otboo.domain.weathernotification.notification.dto.NotificationDto;
import com.sprint.mission.otboo.domain.weathernotification.notification.service.NotificationService;
import com.sprint.mission.otboo.domain.weathernotification.sse.service.SseService;
import com.sprint.mission.otboo.global.event.NotificationLevel;
import com.sprint.mission.otboo.global.event.NotificationRequestedEvent;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationRequestedEventListenerTest {

  private static final FixtureMonkey fixtureMonkey = FixtureMonkey.builder()
      .objectIntrospector(ConstructorPropertiesArbitraryIntrospector.INSTANCE)
      .plugin(new JakartaValidationPlugin())
      .build();

  @InjectMocks
  private NotificationRequestedEventListener notificationRequestedEventListener;

  @Mock
  private NotificationService notificationService;
  @Mock
  private SseService sseService;

  @Nested
  @DisplayName("on")
  class On {

    @Test
    @DisplayName("이벤트를_받으면_알림을_생성하고_그_결과를_notifications_이벤트명으로_SSE_발행한다")
    void 이벤트를_받으면_알림을_생성하고_그_결과를_notifications_이벤트명으로_SSE_발행한다() {
      // given
      NotificationRequestedEvent event = fixtureMonkey.giveMeBuilder(NotificationRequestedEvent.class)
          .set("receiverIds", Set.of(UUID.randomUUID()))
          .set("title", "제목")
          .set("content", "내용")
          .set("level", NotificationLevel.INFO)
          .sample();
      List<NotificationDto> notificationDtos = List.of(new NotificationDto(
          UUID.randomUUID(), Instant.now(), UUID.randomUUID(), "제목", "내용", NotificationLevel.INFO));
      given(notificationService.create(event)).willReturn(notificationDtos);

      // when
      notificationRequestedEventListener.on(event);

      // then
      verify(sseService).send(notificationDtos, "notifications");
    }
  }
}