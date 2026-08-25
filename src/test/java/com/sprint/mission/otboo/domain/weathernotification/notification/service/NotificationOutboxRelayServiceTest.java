package com.sprint.mission.otboo.domain.weathernotification.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import com.sprint.mission.otboo.domain.weathernotification.notification.entity.NotificationOutbox;
import com.sprint.mission.otboo.domain.weathernotification.notification.entity.NotificationOutboxStatus;
import com.sprint.mission.otboo.domain.weathernotification.notification.properties.NotificationOutboxRelayProperties;
import com.sprint.mission.otboo.domain.weathernotification.notification.repository.NotificationOutboxRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

@ExtendWith(MockitoExtension.class)
@DisplayName("NotificationOutboxRelayService")
class NotificationOutboxRelayServiceTest {

  private NotificationOutboxRelayService notificationOutboxRelayService;

  @Mock
  private NotificationOutboxRepository notificationOutboxRepository;
  @Mock
  private KafkaTemplate<String, String> kafkaTemplate;

  private final NotificationOutboxRelayProperties notificationOutboxRelayProperties =
      new NotificationOutboxRelayProperties(100);
  private final Clock clock = Clock.fixed(Instant.parse("2026-08-25T00:00:00Z"), ZoneOffset.UTC);

  @BeforeEach
  void setUp() {
    notificationOutboxRelayService = new NotificationOutboxRelayService(
        notificationOutboxRepository, kafkaTemplate, notificationOutboxRelayProperties, clock);
  }

  @Nested
  @DisplayName("relay")
  class Relay {

    @Test
    @DisplayName("PENDING_outbox를_발행하고_PUBLISHED로_마킹한다")
    void PENDING_outbox를_발행하고_PUBLISHED로_마킹한다() {
      // given
      NotificationOutbox outbox = NotificationOutbox.create("topic", "payload");
      given(notificationOutboxRepository.findByStatusOrderByCreatedAtAsc(
          NotificationOutboxStatus.PENDING, PageRequest.of(0, 100))).willReturn(List.of(outbox));
      @SuppressWarnings("unchecked")
      SendResult<String, String> sendResult = mock(SendResult.class);
      given(kafkaTemplate.send("topic", "payload"))
          .willReturn(CompletableFuture.completedFuture(sendResult));

      // when
      notificationOutboxRelayService.relay();

      // then
      verify(notificationOutboxRepository).save(outbox);
      assertThat(outbox.getStatus()).isEqualTo(NotificationOutboxStatus.PUBLISHED);
      assertThat(outbox.getPublishedAt()).isEqualTo(Instant.parse("2026-08-25T00:00:00Z"));
    }

    @Test
    @DisplayName("발행에_실패하면_PENDING_상태를_유지하고_저장하지_않는다")
    void 발행에_실패하면_PENDING_상태를_유지하고_저장하지_않는다() {
      // given
      NotificationOutbox outbox = NotificationOutbox.create("topic", "payload");
      given(notificationOutboxRepository.findByStatusOrderByCreatedAtAsc(
          NotificationOutboxStatus.PENDING, PageRequest.of(0, 100))).willReturn(List.of(outbox));
      CompletableFuture<SendResult<String, String>> failed = new CompletableFuture<>();
      failed.completeExceptionally(new RuntimeException("전송 실패"));
      given(kafkaTemplate.send("topic", "payload")).willReturn(failed);

      // when
      notificationOutboxRelayService.relay();

      // then
      assertThat(outbox.getStatus()).isEqualTo(NotificationOutboxStatus.PENDING);
      verify(notificationOutboxRepository, never()).save(any());
    }

    @Test
    @DisplayName("PENDING_outbox가_없으면_아무것도_하지_않는다")
    void PENDING_outbox가_없으면_아무것도_하지_않는다() {
      // given
      given(notificationOutboxRepository.findByStatusOrderByCreatedAtAsc(
          NotificationOutboxStatus.PENDING, PageRequest.of(0, 100))).willReturn(List.of());

      // when
      notificationOutboxRelayService.relay();

      // then
      verifyNoInteractions(kafkaTemplate);
    }
  }
}
