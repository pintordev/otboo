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

  @Nested
  @DisplayName("create")
  class Create {

    @Test
    @DisplayName("이벤트의_receiverIds_각각에_대해_Notification을_생성해_저장하고_dto로_반환한다")
    void 이벤트의_receiverIds_각각에_대해_Notification을_생성해_저장하고_dto로_반환한다() {
      // given
      UUID eventId = UUID.randomUUID();
      UUID receiverId1 = UUID.randomUUID();
      UUID receiverId2 = UUID.randomUUID();
      NotificationRequestedEvent event = fixtureMonkey.giveMeBuilder(
              NotificationRequestedEvent.class)
          .set("receiverIds", Set.of(receiverId1, receiverId2))
          .set("title", "제목")
          .set("content", "내용")
          .set("level", NotificationLevel.INFO)
          .sample();
      given(notificationRepository.existsByEventIdAndReceiverId(eventId, receiverId1))
          .willReturn(false);
      given(notificationRepository.existsByEventIdAndReceiverId(eventId, receiverId2))
          .willReturn(false);

      Notification saved1 = entityFixtureMonkey.giveMeBuilder(Notification.class)
          .set("receiverId", receiverId1)
          .set("title", "제목")
          .set("content", "내용")
          .set("level", NotificationLevel.INFO)
          .sample();
      Notification saved2 = entityFixtureMonkey.giveMeBuilder(Notification.class)
          .set("receiverId", receiverId2)
          .set("title", "제목")
          .set("content", "내용")
          .set("level", NotificationLevel.INFO)
          .sample();
      given(notificationRepository.saveAll(anyList())).willReturn(List.of(saved1, saved2));

      NotificationDto dto1 = new NotificationDto(
          saved1.getId(), saved1.getCreatedAt(), receiverId1, "제목", "내용", NotificationLevel.INFO);
      NotificationDto dto2 = new NotificationDto(
          saved2.getId(), saved2.getCreatedAt(), receiverId2, "제목", "내용", NotificationLevel.INFO);
      given(notificationMapper.toDto(saved1)).willReturn(dto1);
      given(notificationMapper.toDto(saved2)).willReturn(dto2);

      // when
      List<NotificationDto> result = notificationService.create(eventId, event);

      // then
      assertThat(result).containsExactlyInAnyOrder(dto1, dto2);

      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
      verify(notificationRepository).saveAll(captor.capture());
      assertThat(captor.getValue())
          .hasSize(2)
          .extracting(Notification::getReceiverId)
          .containsExactlyInAnyOrder(receiverId1, receiverId2);
      assertThat(captor.getValue())
          .allSatisfy(notification -> {
            assertThat(notification.getTitle()).isEqualTo("제목");
            assertThat(notification.getContent()).isEqualTo("내용");
            assertThat(notification.getLevel()).isEqualTo(NotificationLevel.INFO);
          });
    }

    @Test
    @DisplayName("receiverIds_수만큼_Notification_엔티티를_생성해_saveAll에_전달한다")
    void receiverIds_수만큼_Notification_엔티티를_생성해_saveAll에_전달한다() {
      // given
      UUID eventId = UUID.randomUUID();
      UUID receiverId = UUID.randomUUID();
      NotificationRequestedEvent event = fixtureMonkey.giveMeBuilder(
              NotificationRequestedEvent.class)
          .set("receiverIds", Set.of(receiverId))
          .set("title", "제목")
          .set("content", "내용")
          .set("level", NotificationLevel.WARNING)
          .sample();
      given(notificationRepository.existsByEventIdAndReceiverId(eventId, receiverId))
          .willReturn(false);
      given(notificationRepository.saveAll(anyList())).willReturn(List.of());

      // when
      notificationService.create(eventId, event);

      // then
      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
      verify(notificationRepository).saveAll(captor.capture());
      assertThat(captor.getValue()).hasSize(1);
      Notification captured = captor.getValue().get(0);
      assertThat(captured.getReceiverId()).isEqualTo(receiverId);
      assertThat(captured.getTitle()).isEqualTo("제목");
      assertThat(captured.getContent()).isEqualTo("내용");
      assertThat(captured.getLevel()).isEqualTo(NotificationLevel.WARNING);
    }

    @Test
    @DisplayName("이미_처리된_eventId와_receiverId_조합은_건너뛰고_저장하지_않는다")
    void 이미_처리된_eventId와_receiverId_조합은_건너뛰고_저장하지_않는다() {
      // given
      UUID eventId = UUID.randomUUID();
      UUID processedReceiverId = UUID.randomUUID();
      UUID newReceiverId = UUID.randomUUID();
      NotificationRequestedEvent event = fixtureMonkey.giveMeBuilder(
              NotificationRequestedEvent.class)
          .set("receiverIds", Set.of(processedReceiverId, newReceiverId))
          .set("title", "제목")
          .set("content", "내용")
          .set("level", NotificationLevel.INFO)
          .sample();
      given(notificationRepository.existsByEventIdAndReceiverId(eventId, processedReceiverId))
          .willReturn(true);
      given(notificationRepository.existsByEventIdAndReceiverId(eventId, newReceiverId))
          .willReturn(false);
      given(notificationRepository.saveAll(anyList())).willReturn(List.of());

      // when
      notificationService.create(eventId, event);

      // then
      @SuppressWarnings("unchecked")
      ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
      verify(notificationRepository).saveAll(captor.capture());
      assertThat(captor.getValue())
          .hasSize(1)
          .extracting(Notification::getReceiverId)
          .containsExactly(newReceiverId);
    }

    @Test
    @DisplayName("모든_receiverId가_이미_처리됐으면_저장을_생략하고_빈_목록을_반환한다")
    void 모든_receiverId가_이미_처리됐으면_저장을_생략하고_빈_목록을_반환한다() {
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

      // when
      List<NotificationDto> result = notificationService.create(eventId, event);

      // then
      assertThat(result).isEmpty();
      verify(notificationRepository, never()).saveAll(anyList());
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