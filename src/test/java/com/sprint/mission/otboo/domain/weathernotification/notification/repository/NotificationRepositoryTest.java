package com.sprint.mission.otboo.domain.weathernotification.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.domain.weathernotification.notification.entity.Notification;
import com.sprint.mission.otboo.global.config.JpaConfig;
import com.sprint.mission.otboo.global.config.QuerydslConfig;
import com.sprint.mission.otboo.global.event.NotificationLevel;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConfig.class, QuerydslConfig.class})
@DisplayName("NotificationRepository")
class NotificationRepositoryTest {

  @Autowired
  private NotificationRepository notificationRepository;

  @Autowired
  private TestEntityManager testEntityManager;

  @Nested
  @DisplayName("existsByEventIdAndReceiverId")
  class ExistsByEventIdAndReceiverId {

    @Test
    @DisplayName("같은_eventId와_receiverId_조합이_이미_있으면_true를_반환한다")
    void 같은_eventId와_receiverId_조합이_이미_있으면_true를_반환한다() {
      // given
      UUID eventId = UUID.randomUUID();
      UUID receiverId = UUID.randomUUID();
      notificationRepository.save(
          Notification.create(eventId, receiverId, "제목", "내용", NotificationLevel.INFO));
      testEntityManager.flush();
      testEntityManager.clear();

      // when
      boolean result = notificationRepository.existsByEventIdAndReceiverId(eventId, receiverId);

      // then
      assertThat(result).isTrue();
    }

    @Test
    @DisplayName("같은_eventId여도_receiverId가_다르면_false를_반환한다")
    void 같은_eventId여도_receiverId가_다르면_false를_반환한다() {
      // given
      UUID eventId = UUID.randomUUID();
      notificationRepository.save(
          Notification.create(eventId, UUID.randomUUID(), "제목", "내용", NotificationLevel.INFO));
      testEntityManager.flush();
      testEntityManager.clear();

      // when
      boolean result =
          notificationRepository.existsByEventIdAndReceiverId(eventId, UUID.randomUUID());

      // then
      assertThat(result).isFalse();
    }

    @Test
    @DisplayName("조합이_없으면_false를_반환한다")
    void 조합이_없으면_false를_반환한다() {
      // when
      boolean result =
          notificationRepository.existsByEventIdAndReceiverId(UUID.randomUUID(), UUID.randomUUID());

      // then
      assertThat(result).isFalse();
    }
  }

  @Nested
  @DisplayName("findByEventIdAndSseDeliveredAtIsNull")
  class FindByEventIdAndSseDeliveredAtIsNull {

    @Test
    @DisplayName("같은_eventId여도_sse_delivered_at이_NULL이면_미전달_목록에_포함된다")
    void 같은_eventId여도_sse_delivered_at이_NULL이면_미전달_목록에_포함된다() {
      // given
      UUID eventId = UUID.randomUUID();
      Notification undelivered = notificationRepository.save(
          Notification.create(eventId, UUID.randomUUID(), "제목", "내용", NotificationLevel.INFO));
      Notification delivered = notificationRepository.save(
          Notification.create(eventId, UUID.randomUUID(), "제목", "내용", NotificationLevel.INFO));
      delivered.markSseDelivered(Instant.now());
      testEntityManager.flush();
      testEntityManager.clear();

      // when
      List<Notification> result =
          notificationRepository.findByEventIdAndSseDeliveredAtIsNull(eventId);

      // then
      assertThat(result).extracting(Notification::getId).containsExactly(undelivered.getId());
    }

    @Test
    @DisplayName("미전달_알림이_없으면_빈_목록을_반환한다")
    void 미전달_알림이_없으면_빈_목록을_반환한다() {
      // given
      UUID eventId = UUID.randomUUID();
      Notification delivered = notificationRepository.save(
          Notification.create(eventId, UUID.randomUUID(), "제목", "내용", NotificationLevel.INFO));
      delivered.markSseDelivered(Instant.now());
      testEntityManager.flush();
      testEntityManager.clear();

      // when
      List<Notification> result =
          notificationRepository.findByEventIdAndSseDeliveredAtIsNull(eventId);

      // then
      assertThat(result).isEmpty();
    }
  }
}