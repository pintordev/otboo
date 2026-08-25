package com.sprint.mission.otboo.domain.weathernotification.notification.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.ConstructorPropertiesArbitraryIntrospector;
import com.navercorp.fixturemonkey.jakarta.validation.plugin.JakartaValidationPlugin;
import com.sprint.mission.otboo.domain.weathernotification.notification.entity.NotificationOutbox;
import com.sprint.mission.otboo.domain.weathernotification.notification.kafka.NotificationKafkaTopics;
import com.sprint.mission.otboo.domain.weathernotification.notification.kafka.NotificationOutboxPayload;
import com.sprint.mission.otboo.domain.weathernotification.notification.repository.NotificationOutboxRepository;
import com.sprint.mission.otboo.global.event.NotificationLevel;
import com.sprint.mission.otboo.global.event.NotificationRequestedEvent;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

@DisplayName("NotificationRequestedEventListener")
class NotificationRequestedEventListenerTest {

  private final FixtureMonkey fixtureMonkey = FixtureMonkey.builder()
      .objectIntrospector(ConstructorPropertiesArbitraryIntrospector.INSTANCE)
      .plugin(new JakartaValidationPlugin())
      .build();
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final NotificationOutboxRepository notificationOutboxRepository =
      mock(NotificationOutboxRepository.class);
  private final NotificationRequestedEventListener notificationRequestedEventListener =
      new NotificationRequestedEventListener(notificationOutboxRepository, objectMapper);

  @Nested
  @DisplayName("on")
  class On {

    @Test
    @DisplayName("이벤트를_받으면_outbox에_저장한다")
    void 이벤트를_받으면_outbox에_저장한다() {
      // given
      NotificationRequestedEvent event = fixtureMonkey.giveMeBuilder(NotificationRequestedEvent.class)
          .set("receiverIds", Set.of(UUID.randomUUID()))
          .set("title", "제목")
          .set("content", "내용")
          .set("level", NotificationLevel.INFO)
          .sample();

      // when
      notificationRequestedEventListener.on(event);

      // then
      ArgumentCaptor<NotificationOutbox> captor = ArgumentCaptor.forClass(NotificationOutbox.class);
      verify(notificationOutboxRepository).save(captor.capture());
      NotificationOutbox saved = captor.getValue();
      assertThat(saved.getTopic()).isEqualTo(NotificationKafkaTopics.NOTIFICATION_REQUESTED);

      NotificationOutboxPayload payload =
          objectMapper.readValue(saved.getPayload(), NotificationOutboxPayload.class);
      assertThat(payload.eventId()).isNotNull();
      assertThat(payload.event()).isEqualTo(event);
    }
  }
}
