package com.sprint.mission.otboo.domain.weathernotification.notification.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.domain.weathernotification.notification.dto.NotificationDto;
import com.sprint.mission.otboo.domain.weathernotification.notification.entity.Notification;
import com.sprint.mission.otboo.global.event.NotificationLevel;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class NotificationMapperTest {

    private final NotificationMapper notificationMapper = new NotificationMapper();

    @Nested
    @DisplayName("ToDto")
    class ToDto {

        @Test
        @DisplayName("Notification을_NotificationDto로_정확히_변환한다")
        void Notification을_NotificationDto로_정확히_변환한다() {
            // given
            UUID receiverId = UUID.randomUUID();
            Notification notification = Notification.create(receiverId, "제목", "내용", NotificationLevel.WARNING);

            // when
            NotificationDto dto = notificationMapper.toDto(notification);

            // then
            assertThat(dto.id()).isEqualTo(notification.getId());
            assertThat(dto.createdAt()).isEqualTo(notification.getCreatedAt());
            assertThat(dto.receiverId()).isEqualTo(receiverId);
            assertThat(dto.title()).isEqualTo("제목");
            assertThat(dto.content()).isEqualTo("내용");
            assertThat(dto.level()).isEqualTo(NotificationLevel.WARNING);
        }
    }
}