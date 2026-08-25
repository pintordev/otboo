package com.sprint.mission.otboo.domain.weathernotification.notification.event;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.ConstructorPropertiesArbitraryIntrospector;
import com.navercorp.fixturemonkey.jakarta.validation.plugin.JakartaValidationPlugin;
import com.sprint.mission.otboo.domain.weathernotification.notification.kafka.NotificationKafkaTopics;
import com.sprint.mission.otboo.global.event.NotificationLevel;
import com.sprint.mission.otboo.global.event.NotificationRequestedEvent;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import tools.jackson.databind.ObjectMapper;

@DisplayName("NotificationRequestedEventListener")
class NotificationRequestedEventListenerTest {

  private final FixtureMonkey fixtureMonkey = FixtureMonkey.builder()
      .objectIntrospector(ConstructorPropertiesArbitraryIntrospector.INSTANCE)
      .plugin(new JakartaValidationPlugin())
      .build();
  private final ObjectMapper objectMapper = new ObjectMapper();
  private final KafkaTemplate<String, String> kafkaTemplate = mock(KafkaTemplate.class);
  private final NotificationRequestedEventListener notificationRequestedEventListener =
      new NotificationRequestedEventListener(kafkaTemplate, objectMapper);

  @Nested
  @DisplayName("on")
  class On {

    @Test
    @DisplayName("이벤트를_받으면_알림_요청_토픽으로_발행한다")
    void 이벤트를_받으면_알림_요청_토픽으로_발행한다() {
      // given
      NotificationRequestedEvent event = fixtureMonkey.giveMeBuilder(NotificationRequestedEvent.class)
          .set("receiverIds", Set.of(UUID.randomUUID()))
          .set("title", "제목")
          .set("content", "내용")
          .set("level", NotificationLevel.INFO)
          .sample();
      String payload = objectMapper.writeValueAsString(event);
      given(kafkaTemplate.send(NotificationKafkaTopics.NOTIFICATION_REQUESTED, payload))
          .willReturn(CompletableFuture.completedFuture(null));

      // when
      notificationRequestedEventListener.on(event);

      // then
      verify(kafkaTemplate).send(NotificationKafkaTopics.NOTIFICATION_REQUESTED, payload);
    }
  }
}