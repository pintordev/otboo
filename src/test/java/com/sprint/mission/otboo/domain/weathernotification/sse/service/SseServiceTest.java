package com.sprint.mission.otboo.domain.weathernotification.sse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.sprint.mission.otboo.domain.weathernotification.notification.dto.NotificationDto;
import com.sprint.mission.otboo.domain.weathernotification.sse.config.SseConfig;
import com.sprint.mission.otboo.domain.weathernotification.sse.dto.SseMessage;
import com.sprint.mission.otboo.domain.weathernotification.sse.repository.SseEmitterRepository;
import com.sprint.mission.otboo.domain.weathernotification.sse.repository.SseMessageRepository;
import com.sprint.mission.otboo.global.event.NotificationLevel;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import tools.jackson.databind.ObjectMapper;

@ExtendWith(MockitoExtension.class)
class SseServiceTest {

  private SseService sseService;

  @Mock
  private SseEmitterRepository sseEmitterRepository;
  @Mock
  private SseMessageRepository sseMessageRepository;
  @Mock
  private StringRedisTemplate stringRedisTemplate;
  private final ObjectMapper objectMapper = new ObjectMapper();

  @BeforeEach
  void setUp() {
    sseService = new SseService(sseEmitterRepository, sseMessageRepository, stringRedisTemplate,
        objectMapper);
  }

  @Nested
  @DisplayName("connect")
  class Connect {

    @Test
    @DisplayName("emitter를_생성해_repository에_등록하고_생성한_emitter를_반환한다")
    void emitter를_생성해_repository에_등록하고_생성한_emitter를_반환한다() {
      // given
      UUID userId = UUID.randomUUID();
      given(sseMessageRepository.getLatestCreatedAt()).willReturn(null);
      given(sseMessageRepository.findAllAfter(isNull(), eq(userId))).willReturn(List.of());

      try (MockedConstruction<SseEmitter> mocked = mockConstruction(SseEmitter.class)) {
        // when
        SseEmitter result = sseService.connect(userId, null);

        // then
        SseEmitter createdEmitter = mocked.constructed().get(0);
        assertThat(result).isSameAs(createdEmitter);
        verify(sseEmitterRepository).save(userId, createdEmitter, null);
      }
    }

    @Test
    @DisplayName("ping_전송에_실패하면_유실_이벤트_재생을_스킵한다")
    void ping_전송에_실패하면_유실_이벤트_재생을_스킵한다() throws IOException {
      // given
      UUID userId = UUID.randomUUID();
      UUID lastEventId = UUID.randomUUID();
      given(sseMessageRepository.getLatestCreatedAt()).willReturn(null);

      try (MockedConstruction<SseEmitter> mocked = mockConstruction(SseEmitter.class,
          (mock, context) -> doThrow(new IOException("dead"))
              .when(mock).send(any(SseEmitter.SseEventBuilder.class)))) {
        // when
        sseService.connect(userId, lastEventId);

        // then
        verify(sseMessageRepository, never()).findAllAfter(any(), any());
      }
    }

    @Test
    @DisplayName("LastEventId_이후_유실된_이벤트를_전부_재생한다")
    void LastEventId_이후_유실된_이벤트를_전부_재생한다() throws IOException {
      // given
      UUID userId = UUID.randomUUID();
      UUID lastEventId = UUID.randomUUID();
      Instant t1 = Instant.parse("2026-01-01T00:00:01Z");
      Instant t2 = Instant.parse("2026-01-01T00:00:02Z");
      SseMessage message1 = new SseMessage(UUID.randomUUID(), Set.of(userId), "notifications",
          "payload1", t1);
      SseMessage message2 = new SseMessage(UUID.randomUUID(), Set.of(userId), "notifications",
          "payload2", t2);
      given(sseMessageRepository.getLatestCreatedAt()).willReturn(t2);
      given(sseMessageRepository.findAllAfter(lastEventId, userId)).willReturn(
          List.of(message1, message2));

      try (MockedConstruction<SseEmitter> mocked = mockConstruction(SseEmitter.class)) {
        // when
        sseService.connect(userId, lastEventId);

        // then — ping 1회 + 재생 2회
        SseEmitter createdEmitter = mocked.constructed().get(0);
        verify(createdEmitter, times(3)).send(any(SseEmitter.SseEventBuilder.class));
      }
    }

    @Test
    @DisplayName("재생_중_연결_시점_스냅샷_이후에_생성된_이벤트는_재생하지_않는다")
    void 재생_중_연결_시점_스냅샷_이후에_생성된_이벤트는_재생하지_않는다() throws IOException {
      // given
      UUID userId = UUID.randomUUID();
      UUID lastEventId = UUID.randomUUID();
      Instant t1 = Instant.parse("2026-01-01T00:00:01Z");
      Instant snapshotAt = Instant.parse("2026-01-01T00:00:02Z");
      Instant t3 = Instant.parse("2026-01-01T00:00:03Z");
      SseMessage message1 = new SseMessage(UUID.randomUUID(), Set.of(userId), "notifications",
          "payload1", t1);
      SseMessage message2 = new SseMessage(UUID.randomUUID(), Set.of(userId), "notifications",
          "payload2", snapshotAt);
      SseMessage message3 = new SseMessage(UUID.randomUUID(), Set.of(userId), "notifications",
          "payload3", t3);
      given(sseMessageRepository.getLatestCreatedAt()).willReturn(snapshotAt);
      given(sseMessageRepository.findAllAfter(lastEventId, userId))
          .willReturn(List.of(message1, message2, message3));

      try (MockedConstruction<SseEmitter> mocked = mockConstruction(SseEmitter.class)) {
        // when
        sseService.connect(userId, lastEventId);

        // then — ping 1회 + message1, message2까지만 재생(message3은 재생하지 않음)
        SseEmitter createdEmitter = mocked.constructed().get(0);
        verify(createdEmitter, times(3)).send(any(SseEmitter.SseEventBuilder.class));
      }
    }

    @Test
    @DisplayName("연결_시점_전역_최신_이벤트가_다른_사용자_대상이라_missed_목록에_없어도_스냅샷_이후_생성된_이_사용자_이벤트는_재생하지_않는다")
    void 연결_시점_전역_최신_이벤트가_다른_사용자_대상이라_missed_목록에_없어도_스냅샷_이후_생성된_이_사용자_이벤트는_재생하지_않는다()
        throws IOException {
      // given — 전역 최신 이벤트(스냅샷 시각의 근거)는 다른 사용자 대상이라 이 사용자의 missed 목록엔 존재하지 않는다
      UUID userId = UUID.randomUUID();
      UUID lastEventId = UUID.randomUUID();
      Instant beforeSnapshot = Instant.parse("2026-01-01T00:00:01Z");
      Instant snapshotAt = Instant.parse("2026-01-01T00:00:02Z");
      Instant afterSnapshot = Instant.parse("2026-01-01T00:00:03Z");
      SseMessage message1 = new SseMessage(UUID.randomUUID(), Set.of(userId), "notifications",
          "payload1", beforeSnapshot);
      SseMessage message2 = new SseMessage(UUID.randomUUID(), Set.of(userId), "notifications",
          "payload2", afterSnapshot);
      given(sseMessageRepository.getLatestCreatedAt()).willReturn(snapshotAt);
      given(sseMessageRepository.findAllAfter(lastEventId, userId))
          .willReturn(List.of(message1, message2));

      try (MockedConstruction<SseEmitter> mocked = mockConstruction(SseEmitter.class)) {
        // when
        sseService.connect(userId, lastEventId);

        // then — ping 1회 + message1만 재생(message2는 이미 실시간 push된 것으로 간주해 재생 제외, 중복 없음)
        SseEmitter createdEmitter = mocked.constructed().get(0);
        verify(createdEmitter, times(2)).send(any(SseEmitter.SseEventBuilder.class));
      }
    }
  }

  @Nested
  @DisplayName("disconnect")
  class Disconnect {

    @Test
    @DisplayName("해당_유저의_emitter가_있으면_complete를_호출한다")
    void 해당_유저의_emitter가_있으면_complete를_호출한다() {
      // given
      UUID userId = UUID.randomUUID();
      SseEmitter emitter = mock(SseEmitter.class);
      given(sseEmitterRepository.findByUserId(userId)).willReturn(Optional.of(emitter));

      // when
      sseService.disconnect(userId);

      // then
      verify(emitter).complete();
    }

    @Test
    @DisplayName("해당_유저의_emitter가_없으면_아무_것도_하지_않는다")
    void 해당_유저의_emitter가_없으면_아무_것도_하지_않는다() {
      // given
      UUID userId = UUID.randomUUID();
      given(sseEmitterRepository.findByUserId(userId)).willReturn(Optional.empty());

      // when & then — 예외 없이 정상 종료
      sseService.disconnect(userId);
    }

    @Test
    @DisplayName("다른_유저의_emitter는_건드리지_않는다")
    void 다른_유저의_emitter는_건드리지_않는다() {
      // given
      UUID targetUserId = UUID.randomUUID();
      UUID otherUserId = UUID.randomUUID();
      SseEmitter targetEmitter = mock(SseEmitter.class);
      SseEmitter otherEmitter = mock(SseEmitter.class);
      given(sseEmitterRepository.findByUserId(targetUserId)).willReturn(Optional.of(targetEmitter));

      // when
      sseService.disconnect(targetUserId);

      // then
      verify(targetEmitter).complete();
      verify(otherEmitter, never()).complete();
      verify(sseEmitterRepository, never()).findByUserId(otherUserId);
    }
  }

  @Nested
  @DisplayName("cleanUp")
  class CleanUp {

    @Test
    @DisplayName("등록된_모든_emitter에_ping을_전송한다")
    void 등록된_모든_emitter에_ping을_전송한다() throws IOException {
      // given
      UUID userId1 = UUID.randomUUID();
      UUID userId2 = UUID.randomUUID();
      SseEmitter emitter1 = mock(SseEmitter.class);
      SseEmitter emitter2 = mock(SseEmitter.class);
      given(sseEmitterRepository.findAll()).willReturn(
          Map.of(userId1, emitter1, userId2, emitter2));

      // when
      sseService.cleanUp();

      // then
      verify(emitter1).send(any(SseEmitter.SseEventBuilder.class));
      verify(emitter2).send(any(SseEmitter.SseEventBuilder.class));
    }
  }

  @Nested
  @DisplayName("send")
  class Send {

    @Test
    @DisplayName("메시지를_저장하고_Redis_채널에_발행한다")
    void 메시지를_저장하고_Redis_채널에_발행한다() {
      // given
      UUID receiverId = UUID.randomUUID();
      NotificationDto dto = new NotificationDto(
          UUID.randomUUID(), Instant.now(), receiverId, "제목", "내용", NotificationLevel.INFO);

      // when
      sseService.send(List.of(dto), "notifications");

      // then
      verify(sseMessageRepository).save(any(SseMessage.class));
      verify(stringRedisTemplate).convertAndSend(eq(SseConfig.SSE_CHANNEL), anyString());
    }

    @Test
    @DisplayName("수신자별로_각각_메시지를_저장하고_발행한다")
    void 수신자별로_각각_메시지를_저장하고_발행한다() {
      // given
      UUID receiverId1 = UUID.randomUUID();
      UUID receiverId2 = UUID.randomUUID();
      NotificationDto dto1 = new NotificationDto(
          UUID.randomUUID(), Instant.now(), receiverId1, "제목1", "내용1", NotificationLevel.INFO);
      NotificationDto dto2 = new NotificationDto(
          UUID.randomUUID(), Instant.now(), receiverId2, "제목2", "내용2", NotificationLevel.INFO);

      // when
      sseService.send(List.of(dto1, dto2), "notifications");

      // then
      verify(sseMessageRepository, times(2)).save(any(SseMessage.class));
      verify(stringRedisTemplate, times(2)).convertAndSend(eq(SseConfig.SSE_CHANNEL), anyString());
    }
  }

  @Nested
  @DisplayName("로컬 전달(구독자 콜백)")
  class DeliverLocally {

    @Test
    @DisplayName("로컬에_연결된_emitter로_전송한다")
    void 로컬에_연결된_emitter로_전송한다() throws IOException {
      // given
      UUID userId = UUID.randomUUID();
      SseMessage message = new SseMessage(Set.of(userId), "notifications", "payload");
      SseEmitter emitter = mock(SseEmitter.class);
      given(sseEmitterRepository.findSnapshotAt(userId)).willReturn(Optional.empty());
      given(sseEmitterRepository.findByUserId(userId)).willReturn(Optional.of(emitter));

      // when
      sseService.deliverLocally(message);

      // then
      verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("재생_스냅샷_이전_메시지는_중복_전송하지_않는다")
    void 재생_스냅샷_이전_메시지는_중복_전송하지_않는다() {
      // given — save()와 로컬 전송 사이 Pub/Sub 왕복 구간에 connect()가 끼어들어
      // 이미 재생으로 처리된 것과 같은 메시지가 뒤늦게 도착하는 상황을 재현
      UUID userId = UUID.randomUUID();
      Instant snapshotAt = Instant.now();
      SseMessage alreadyReplayed = new SseMessage(
          UUID.randomUUID(), Set.of(userId), "notifications", "payload",
          snapshotAt.minusSeconds(1));
      given(sseEmitterRepository.findSnapshotAt(userId)).willReturn(Optional.of(snapshotAt));

      // when
      sseService.deliverLocally(alreadyReplayed);

      // then
      verify(sseEmitterRepository, never()).findByUserId(userId);
    }
  }
}