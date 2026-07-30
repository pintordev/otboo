package com.sprint.mission.otboo.domain.weathernotification.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.ConstructorPropertiesArbitraryIntrospector;
import com.navercorp.fixturemonkey.jakarta.validation.plugin.JakartaValidationPlugin;
import com.sprint.mission.otboo.domain.weathernotification.notification.dto.NotificationDto;
import com.sprint.mission.otboo.domain.weathernotification.notification.entity.Notification;
import com.sprint.mission.otboo.domain.weathernotification.notification.mapper.NotificationMapper;
import com.sprint.mission.otboo.domain.weathernotification.notification.repository.NotificationRepository;
import com.sprint.mission.otboo.global.event.NotificationLevel;
import com.sprint.mission.otboo.global.event.NotificationRequestedEvent;
import java.util.List;
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
class NotificationServiceTest {

  private static final FixtureMonkey fixtureMonkey = FixtureMonkey.builder()
      .objectIntrospector(ConstructorPropertiesArbitraryIntrospector.INSTANCE)
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
      UUID receiverId1 = UUID.randomUUID();
      UUID receiverId2 = UUID.randomUUID();
      NotificationRequestedEvent event = fixtureMonkey.giveMeBuilder(
              NotificationRequestedEvent.class)
          .set("receiverIds", Set.of(receiverId1, receiverId2))
          .set("title", "제목")
          .set("content", "내용")
          .set("level", NotificationLevel.INFO)
          .sample();

      Notification saved1 = Notification.create(receiverId1, "제목", "내용", NotificationLevel.INFO);
      Notification saved2 = Notification.create(receiverId2, "제목", "내용", NotificationLevel.INFO);
      given(notificationRepository.saveAll(anyList())).willReturn(List.of(saved1, saved2));

      NotificationDto dto1 = new NotificationDto(
          saved1.getId(), saved1.getCreatedAt(), receiverId1, "제목", "내용", NotificationLevel.INFO);
      NotificationDto dto2 = new NotificationDto(
          saved2.getId(), saved2.getCreatedAt(), receiverId2, "제목", "내용", NotificationLevel.INFO);
      given(notificationMapper.toDto(saved1)).willReturn(dto1);
      given(notificationMapper.toDto(saved2)).willReturn(dto2);

      // when
      List<NotificationDto> result = notificationService.create(event);

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
      UUID receiverId = UUID.randomUUID();
      NotificationRequestedEvent event = fixtureMonkey.giveMeBuilder(
              NotificationRequestedEvent.class)
          .set("receiverIds", Set.of(receiverId))
          .set("title", "제목")
          .set("content", "내용")
          .set("level", NotificationLevel.WARNING)
          .sample();
      given(notificationRepository.saveAll(anyList())).willReturn(List.of());

      // when
      notificationService.create(event);

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
  }
}