package com.sprint.mission.otboo.domain.weathernotification.notification.repository.querydsl;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.domain.weathernotification.notification.dto.NotificationDto;
import com.sprint.mission.otboo.domain.weathernotification.notification.dto.NotificationListParams;
import com.sprint.mission.otboo.domain.weathernotification.notification.entity.Notification;
import com.sprint.mission.otboo.domain.weathernotification.notification.repository.NotificationRepository;
import com.sprint.mission.otboo.global.config.JpaConfig;
import com.sprint.mission.otboo.global.config.QuerydslConfig;
import com.sprint.mission.otboo.global.dto.CursorPageResponse;
import com.sprint.mission.otboo.global.event.NotificationLevel;
import java.time.Instant;
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
@DisplayName("NotificationCustomRepository")
class NotificationCustomRepositoryTest {

  @Autowired
  private NotificationRepository notificationRepository;

  @Autowired
  private TestEntityManager testEntityManager;

  private Notification saveNotification(UUID receiverId) {
    return notificationRepository.save(
        Notification.create(receiverId, "제목", "내용", NotificationLevel.INFO));
  }

  private void setCreatedAt(UUID notificationId, Instant createdAt) {
    testEntityManager.getEntityManager()
        .createNativeQuery("update notifications set created_at = :createdAt where id = :id")
        .setParameter("createdAt", createdAt)
        .setParameter("id", notificationId)
        .executeUpdate();
  }

  @Nested
  @DisplayName("findNotifications")
  class FindNotifications {

    @Test
    @DisplayName("커서가 없으면 최신순으로 limit개만 반환하고 hasNext를 true로 계산한다")
    void 커서가_없으면_최신순으로_limit개만_반환하고_hasNext를_true로_계산한다() {
      // given
      UUID receiverId = UUID.randomUUID();
      for (int i = 0; i < 3; i++) {
        saveNotification(receiverId);
      }
      testEntityManager.flush();
      testEntityManager.clear();

      NotificationListParams params = new NotificationListParams(null, null, 2);

      // when
      CursorPageResponse<NotificationDto> result =
          notificationRepository.findNotifications(receiverId, params);

      // then
      assertThat(result.data()).hasSize(2);
      assertThat(result.hasNext()).isTrue();
      assertThat(result.totalCount()).isEqualTo(3);
      assertThat(result.sortBy()).isEqualTo("createdAt");
    }

    @Test
    @DisplayName("커서 이후의 알림만 조회한다")
    void 커서_이후의_알림만_조회한다() {
      // given
      UUID receiverId = UUID.randomUUID();
      Notification first = saveNotification(receiverId);
      Notification second = saveNotification(receiverId);
      Notification third = saveNotification(receiverId);
      testEntityManager.flush();

      Instant firstCreatedAt = Instant.parse("2026-07-28T00:00:00Z");
      Instant secondCreatedAt = Instant.parse("2026-07-28T00:00:01Z");
      Instant thirdCreatedAt = Instant.parse("2026-07-28T00:00:02Z");
      setCreatedAt(first.getId(), firstCreatedAt);
      setCreatedAt(second.getId(), secondCreatedAt);
      setCreatedAt(third.getId(), thirdCreatedAt);
      testEntityManager.clear();

      // DESC 정렬 시 순서: third → second → first
      NotificationListParams params = new NotificationListParams(
          thirdCreatedAt.toString(), third.getId(), 10);

      // when
      CursorPageResponse<NotificationDto> result =
          notificationRepository.findNotifications(receiverId, params);

      // then
      assertThat(result.data())
          .extracting(NotificationDto::id)
          .containsExactly(second.getId(), first.getId());
    }

    @Test
    @DisplayName("totalCount는 페이지 크기가 아닌 실제 전체 건수를 반환한다")
    void totalCount는_페이지_크기가_아닌_실제_전체_건수를_반환한다() {
      // given
      UUID receiverId = UUID.randomUUID();
      for (int i = 0; i < 25; i++) {
        saveNotification(receiverId);
      }
      testEntityManager.flush();
      testEntityManager.clear();

      NotificationListParams params = new NotificationListParams(null, null, 20);

      // when
      CursorPageResponse<NotificationDto> result =
          notificationRepository.findNotifications(receiverId, params);

      // then
      assertThat(result.data()).hasSize(20);
      assertThat(result.totalCount()).isEqualTo(25);
    }

    @Test
    @DisplayName("다른 유저의 알림은 섞이지 않는다")
    void 다른_유저의_알림은_섞이지_않는다() {
      // given
      UUID targetReceiver = UUID.randomUUID();
      UUID otherReceiver = UUID.randomUUID();
      Notification target = saveNotification(targetReceiver);
      saveNotification(otherReceiver);
      testEntityManager.flush();
      testEntityManager.clear();

      NotificationListParams params = new NotificationListParams(null, null, 10);

      // when
      CursorPageResponse<NotificationDto> result =
          notificationRepository.findNotifications(targetReceiver, params);

      // then
      assertThat(result.data())
          .extracting(NotificationDto::id)
          .containsExactly(target.getId());
    }

    @Test
    @DisplayName("createdAt이 같으면 id 역순으로 tie-break하여 정렬한다")
    void createdAt이_같으면_id_역순으로_tie_break하여_정렬한다() {
      // given
      UUID receiverId = UUID.randomUUID();
      Instant sameTime = Instant.parse("2026-07-28T00:00:00Z");
      Notification a = saveNotification(receiverId);
      Notification b = saveNotification(receiverId);
      testEntityManager.flush();

      setCreatedAt(a.getId(), sameTime);
      setCreatedAt(b.getId(), sameTime);
      testEntityManager.clear();

      NotificationListParams params = new NotificationListParams(null, null, 10);

      // when
      CursorPageResponse<NotificationDto> result =
          notificationRepository.findNotifications(receiverId, params);

      // then
      assertThat(result.data())
          .extracting(NotificationDto::id)
          .containsExactlyInAnyOrder(a.getId(), b.getId());
      assertThat(result.data().get(0).id().toString())
          .isGreaterThan(result.data().get(1).id().toString());
    }

    @Test
    @DisplayName("createdAt 동률에서 커서로 다음 페이지를 조회하면 중복·누락 없이 반환한다")
    void createdAt_동률에서_커서로_다음_페이지를_조회하면_중복_누락_없이_반환한다() {
      // given
      UUID receiverId = UUID.randomUUID();
      Instant sameTime = Instant.parse("2026-07-28T00:00:00Z");
      Notification a = saveNotification(receiverId);
      Notification b = saveNotification(receiverId);
      testEntityManager.flush();

      setCreatedAt(a.getId(), sameTime);
      setCreatedAt(b.getId(), sameTime);
      testEntityManager.clear();

      NotificationListParams firstPage = new NotificationListParams(null, null, 1);

      // when: 첫 페이지 조회
      CursorPageResponse<NotificationDto> first =
          notificationRepository.findNotifications(receiverId, firstPage);

      // then
      assertThat(first.data()).hasSize(1);
      assertThat(first.hasNext()).isTrue();
      assertThat(first.nextCursor()).isNotNull();
      assertThat(first.nextIdAfter()).isNotNull();

      UUID firstId = first.data().get(0).id();

      // when: 다음 페이지 조회
      NotificationListParams secondPage =
          new NotificationListParams(first.nextCursor(), first.nextIdAfter(), 1);
      CursorPageResponse<NotificationDto> second =
          notificationRepository.findNotifications(receiverId, secondPage);

      // then
      assertThat(second.data()).hasSize(1);
      UUID secondId = second.data().get(0).id();
      assertThat(secondId).isNotEqualTo(firstId);
    }
  }
}