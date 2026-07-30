package com.sprint.mission.otboo.domain.weathernotification.notification.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.FieldReflectionArbitraryIntrospector;
import com.navercorp.fixturemonkey.jakarta.validation.plugin.JakartaValidationPlugin;
import com.sprint.mission.otboo.domain.weathernotification.notification.dto.NotificationDto;
import com.sprint.mission.otboo.domain.weathernotification.notification.entity.Notification;
import com.sprint.mission.otboo.global.event.NotificationLevel;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class NotificationMapperTest {

  private static final FixtureMonkey entityFixtureMonkey = FixtureMonkey.builder()
      .objectIntrospector(FieldReflectionArbitraryIntrospector.INSTANCE)
      .plugin(new JakartaValidationPlugin())
      .build();

  private final NotificationMapper notificationMapper = new NotificationMapper();

  @Nested
  @DisplayName("ToDto")
  class ToDto {

    @Test
    @DisplayName("Notification을_NotificationDto로_정확히_변환한다")
    void Notification을_NotificationDto로_정확히_변환한다() {
      // given
      UUID id = UUID.randomUUID();
      Instant createdAt = Instant.now();
      UUID receiverId = UUID.randomUUID();
      Notification notification = entityFixtureMonkey.giveMeBuilder(Notification.class)
          .set("id", id)
          .set("createdAt", createdAt)
          .set("receiverId", receiverId)
          .set("title", "제목")
          .set("content", "내용")
          .set("level", NotificationLevel.WARNING)
          .sample();

      // when
      NotificationDto dto = notificationMapper.toDto(notification);

      // then
      assertThat(dto.id()).isEqualTo(id);
      assertThat(dto.createdAt()).isEqualTo(createdAt);
      assertThat(dto.receiverId()).isEqualTo(receiverId);
      assertThat(dto.title()).isEqualTo("제목");
      assertThat(dto.content()).isEqualTo("내용");
      assertThat(dto.level()).isEqualTo(NotificationLevel.WARNING);
    }
  }
}