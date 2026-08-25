package com.sprint.mission.otboo.domain.weathernotification.notification.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.domain.weathernotification.notification.entity.NotificationOutbox;
import com.sprint.mission.otboo.domain.weathernotification.notification.entity.NotificationOutboxStatus;
import com.sprint.mission.otboo.global.config.JpaConfig;
import com.sprint.mission.otboo.global.config.QuerydslConfig;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({JpaConfig.class, QuerydslConfig.class})
@DisplayName("NotificationOutboxRepository")
class NotificationOutboxRepositoryTest {

  @Autowired
  private NotificationOutboxRepository notificationOutboxRepository;

  @Autowired
  private TestEntityManager testEntityManager;

  @Nested
  @DisplayName("findByStatusOrderByCreatedAtAsc")
  class FindByStatusOrderByCreatedAtAsc {

    @Test
    @DisplayName("PENDING_상태만_생성_순서대로_반환한다")
    void PENDING_상태만_생성_순서대로_반환한다() {
      // given
      NotificationOutbox pending1 = notificationOutboxRepository.save(
          NotificationOutbox.create("topic", "payload1"));
      testEntityManager.flush();
      NotificationOutbox pending2 = notificationOutboxRepository.save(
          NotificationOutbox.create("topic", "payload2"));
      testEntityManager.flush();
      NotificationOutbox published = notificationOutboxRepository.save(
          NotificationOutbox.create("topic", "payload3"));
      published.markPublished(Instant.now());
      testEntityManager.flush();
      testEntityManager.clear();

      // when
      List<NotificationOutbox> result = notificationOutboxRepository
          .findByStatusOrderByCreatedAtAsc(NotificationOutboxStatus.PENDING, PageRequest.of(0, 100));

      // then
      assertThat(result)
          .extracting(NotificationOutbox::getId)
          .containsExactly(pending1.getId(), pending2.getId());
    }

    @Test
    @DisplayName("PENDING_상태가_없으면_빈_목록을_반환한다")
    void PENDING_상태가_없으면_빈_목록을_반환한다() {
      // when
      List<NotificationOutbox> result = notificationOutboxRepository
          .findByStatusOrderByCreatedAtAsc(NotificationOutboxStatus.PENDING, PageRequest.of(0, 100));

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("PENDING_상태가_페이지_크기보다_많으면_페이지_크기만큼만_반환한다")
    void PENDING_상태가_페이지_크기보다_많으면_페이지_크기만큼만_반환한다() {
      // given
      NotificationOutbox pending1 = notificationOutboxRepository.save(
          NotificationOutbox.create("topic", "payload1"));
      testEntityManager.flush();
      notificationOutboxRepository.save(NotificationOutbox.create("topic", "payload2"));
      testEntityManager.flush();
      testEntityManager.clear();

      // when
      List<NotificationOutbox> result = notificationOutboxRepository
          .findByStatusOrderByCreatedAtAsc(NotificationOutboxStatus.PENDING, PageRequest.of(0, 1));

      // then
      assertThat(result)
          .extracting(NotificationOutbox::getId)
          .containsExactly(pending1.getId());
    }
  }
}