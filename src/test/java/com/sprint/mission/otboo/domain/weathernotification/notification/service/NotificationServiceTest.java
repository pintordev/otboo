package com.sprint.mission.otboo.domain.weathernotification.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.ConstructorPropertiesArbitraryIntrospector;
import com.navercorp.fixturemonkey.api.introspector.FieldReflectionArbitraryIntrospector;
import com.navercorp.fixturemonkey.jakarta.validation.plugin.JakartaValidationPlugin;
import com.sprint.mission.otboo.domain.weathernotification.notification.dto.NotificationDto;
import com.sprint.mission.otboo.domain.weathernotification.notification.dto.NotificationListParams;
import com.sprint.mission.otboo.domain.weathernotification.notification.entity.Notification;
import com.sprint.mission.otboo.domain.weathernotification.notification.exception.NotificationForbiddenException;
import com.sprint.mission.otboo.domain.weathernotification.notification.exception.NotificationNotFoundException;
import com.sprint.mission.otboo.domain.weathernotification.notification.mapper.NotificationMapper;
import com.sprint.mission.otboo.domain.weathernotification.notification.repository.NotificationRepository;
import com.sprint.mission.otboo.global.dto.CursorPageResponse;
import com.sprint.mission.otboo.global.dto.SortDirection;
import com.sprint.mission.otboo.global.event.NotificationLevel;
import com.sprint.mission.otboo.global.event.NotificationRequestedEvent;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationService")
class NotificationServiceTest {

  private static final FixtureMonkey fixtureMonkey = FixtureMonkey.builder()
      .objectIntrospector(ConstructorPropertiesArbitraryIntrospector.INSTANCE)
      .plugin(new JakartaValidationPlugin())
      .build();

  private static final FixtureMonkey entityFixtureMonkey = FixtureMonkey.builder()
      .objectIntrospector(FieldReflectionArbitraryIntrospector.INSTANCE)
      .plugin(new JakartaValidationPlugin())
      .build();

  @InjectMocks
  private NotificationService notificationService;

  @Mock
  private NotificationRepository notificationRepository;
  @Mock
  private NotificationMapper notificationMapper;
  @Mock
  private NotificationBatchWriter notificationBatchWriter;
  @Mock
  private Clock clock;

  @Nested
  @DisplayName("생성 및 미전달 알림 조회")
  class CreateAndFindUndelivered {

    @Test
    @DisplayName("신규_생성분과_기존_미전달분을_합쳐_반환한다")
    void 신규_생성분과_기존_미전달분을_합쳐_반환한다() {
      // given
      UUID eventId = UUID.randomUUID();
      UUID newReceiverId = UUID.randomUUID();
      NotificationRequestedEvent event = fixtureMonkey.giveMeBuilder(
              NotificationRequestedEvent.class)
          .set("receiverIds", Set.of(newReceiverId))
          .set("title", "제목")
          .set("content", "내용")
          .set("level", NotificationLevel.INFO)
          .sample();
      given(notificationRepository.existsByEventIdAndReceiverId(eventId, newReceiverId))
          .willReturn(false);

      Notification newlySaved = entityFixtureMonkey.giveMeBuilder(Notification.class)
          .set("receiverId", newReceiverId)
          .sample();

      Notification stillUndelivered = entityFixtureMonkey.giveMeBuilder(Notification.class)
          .sample();
      given(notificationRepository.findByEventIdAndSseDeliveredAtIsNull(eventId))
          .willReturn(List.of(newlySaved, stillUndelivered));

      NotificationDto dto1 = new NotificationDto(newlySaved.getId(), newlySaved.getCreatedAt(),
          newReceiverId, "제목", "내용", NotificationLevel.INFO);
      NotificationDto dto2 = new NotificationDto(stillUndelivered.getId(),
          stillUndelivered.getCreatedAt(), stillUndelivered.getReceiverId(),
          stillUndelivered.getTitle(), stillUndelivered.getContent(), stillUndelivered.getLevel());
      given(notificationMapper.toDto(newlySaved)).willReturn(dto1);
      given(notificationMapper.toDto(stillUndelivered)).willReturn(dto2);

      // when
      List<NotificationDto> result =
          notificationService.createAndFindUndelivered(eventId, event);

      // then
      assertThat(result).containsExactlyInAnyOrder(dto1, dto2);
      verify(notificationBatchWriter).saveAll(anyList());
      verify(notificationRepository).findByEventIdAndSseDeliveredAtIsNull(eventId);
    }

    @Test
    @DisplayName("모든_receiverId가_이미_처리됐으면_저장_없이_기존_미전달분만_반환한다")
    void 모든_receiverId가_이미_처리됐으면_저장_없이_기존_미전달분만_반환한다() {
      // given
      UUID eventId = UUID.randomUUID();
      UUID receiverId = UUID.randomUUID();
      NotificationRequestedEvent event = fixtureMonkey.giveMeBuilder(
              NotificationRequestedEvent.class)
          .set("receiverIds", Set.of(receiverId))
          .set("title", "제목")
          .set("content", "내용")
          .set("level", NotificationLevel.INFO)
          .sample();
      given(notificationRepository.existsByEventIdAndReceiverId(eventId, receiverId))
          .willReturn(true);

      Notification undelivered = entityFixtureMonkey.giveMeBuilder(Notification.class).sample();
      given(notificationRepository.findByEventIdAndSseDeliveredAtIsNull(eventId))
          .willReturn(List.of(undelivered));
      NotificationDto dto = new NotificationDto(undelivered.getId(), undelivered.getCreatedAt(),
          undelivered.getReceiverId(), undelivered.getTitle(), undelivered.getContent(),
          undelivered.getLevel());
      given(notificationMapper.toDto(undelivered)).willReturn(dto);

      // when
      List<NotificationDto> result =
          notificationService.createAndFindUndelivered(eventId, event);

      // then
      assertThat(result).containsExactly(dto);
      verify(notificationBatchWriter).saveAll(List.of());
    }
  }

  @Nested
  @DisplayName("SSE 전달 완료 표시")
  class MarkSseDelivered {

    @Test
    @DisplayName("전달된_알림들의_sse_delivered_at을_채운다")
    void 전달된_알림들의_sse_delivered_at을_채운다() {
      // given
      UUID id1 = UUID.randomUUID();
      UUID id2 = UUID.randomUUID();
      Notification notification1 =
          entityFixtureMonkey.giveMeBuilder(Notification.class).set("id", id1).sample();
      Notification notification2 =
          entityFixtureMonkey.giveMeBuilder(Notification.class).set("id", id2).sample();
      Instant now = Instant.parse("2026-01-01T00:00:00Z");
      given(clock.instant()).willReturn(now);
      given(notificationRepository.findAllById(List.of(id1, id2)))
          .willReturn(List.of(notification1, notification2));

      // when
      notificationService.markSseDelivered(List.of(id1, id2));

      // then
      assertThat(notification1.getSseDeliveredAt()).isEqualTo(now);
      assertThat(notification2.getSseDeliveredAt()).isEqualTo(now);
    }

    @Test
    @DisplayName("빈_목록이면_조회하지_않는다")
    void 빈_목록이면_조회하지_않는다() {
      // when
      notificationService.markSseDelivered(List.of());

      // then
      verify(notificationRepository, never()).findAllById(any());
    }
  }

  @Nested
  @DisplayName("알림 목록 조회")
  class GetNotifications {

    @Test
    @DisplayName("Repository가 반환한 페이지를 그대로 전달한다")
    void Repository가_반환한_페이지를_그대로_전달한다() {
      // given
      UUID receiverId = UUID.randomUUID();
      NotificationListParams params = new NotificationListParams(null, null, 10);
      NotificationDto dto = new NotificationDto(
          UUID.randomUUID(), Instant.now(), receiverId, "제목", "내용", NotificationLevel.INFO);
      CursorPageResponse<NotificationDto> repoPage = new CursorPageResponse<>(
          List.of(dto), null, null, false, 1L, "createdAt", SortDirection.DESCENDING);
      when(notificationRepository.findNotifications(receiverId, params)).thenReturn(repoPage);

      // when
      CursorPageResponse<NotificationDto> result =
          notificationService.getNotifications(receiverId, params);

      // then
      assertThat(result).isEqualTo(repoPage);
      verify(notificationRepository).findNotifications(receiverId, params);
    }
  }

  @Nested
  @DisplayName("알림 삭제")
  class Delete {

    @Test
    @DisplayName("본인_알림이면_삭제한다")
    void 본인_알림이면_삭제한다() {
      // given
      UUID receiverId = UUID.randomUUID();
      UUID notificationId = UUID.randomUUID();
      Notification notification = entityFixtureMonkey.giveMeBuilder(Notification.class)
          .set("id", notificationId)
          .set("receiverId", receiverId)
          .sample();
      given(notificationRepository.findById(notificationId)).willReturn(Optional.of(notification));

      // when
      notificationService.delete(notificationId, receiverId);

      // then
      verify(notificationRepository).delete(notification);
    }

    @Test
    @DisplayName("대상_알림이_없으면_NotificationNotFoundException을_던지고_삭제하지_않는다")
    void 대상_알림이_없으면_NotificationNotFoundException을_던지고_삭제하지_않는다() {
      // given
      UUID notificationId = UUID.randomUUID();
      UUID currentUserId = UUID.randomUUID();
      given(notificationRepository.findById(notificationId)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> notificationService.delete(notificationId, currentUserId))
          .isInstanceOf(NotificationNotFoundException.class);
      verify(notificationRepository, never()).delete(any());
    }

    @Test
    @DisplayName("본인_알림이_아니면_NotificationForbiddenException을_던지고_삭제하지_않는다")
    void 본인_알림이_아니면_NotificationForbiddenException을_던지고_삭제하지_않는다() {
      // given
      UUID receiverId = UUID.randomUUID();
      UUID currentUserId = UUID.randomUUID();
      UUID notificationId = UUID.randomUUID();
      Notification notification = entityFixtureMonkey.giveMeBuilder(Notification.class)
          .set("id", notificationId)
          .set("receiverId", receiverId)
          .sample();
      given(notificationRepository.findById(notificationId)).willReturn(Optional.of(notification));

      // when & then
      assertThatThrownBy(() -> notificationService.delete(notificationId, currentUserId))
          .isInstanceOf(NotificationForbiddenException.class);
      verify(notificationRepository, never()).delete(any());
    }
  }
}