package com.sprint.mission.otboo.domain.weathernotification.sse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@ExtendWith(MockitoExtension.class)
class SseServiceTest {

  private SseService sseService;

  @Mock
  private SseEmitterRepository sseEmitterRepository;
  @Mock
  private SseMessageRepository sseMessageRepository;

  @BeforeEach
  void setUp() {
    sseService = new SseService(sseEmitterRepository, sseMessageRepository);
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
        verify(sseEmitterRepository).save(userId, createdEmitter);
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
    @DisplayName("dto를_메시지로_저장하고_수신자_emitter가_있으면_전송한다")
    void dto를_메시지로_저장하고_수신자_emitter가_있으면_전송한다() throws IOException {
      // given
      UUID receiverId = UUID.randomUUID();
      NotificationDto dto = new NotificationDto(
          UUID.randomUUID(), Instant.now(), receiverId, "제목", "내용", NotificationLevel.INFO);
      SseEmitter emitter = mock(SseEmitter.class);
      given(sseEmitterRepository.findByUserId(receiverId)).willReturn(Optional.of(emitter));

      // when
      sseService.send(List.of(dto), "notifications");

      // then
      verify(sseMessageRepository).save(any(SseMessage.class));
      verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("수신자별로_각자_자기_emitter에만_개별_전송한다")
    void 수신자별로_각자_자기_emitter에만_개별_전송한다() throws IOException {
      // given
      UUID receiverId1 = UUID.randomUUID();
      UUID receiverId2 = UUID.randomUUID();
      NotificationDto dto1 = new NotificationDto(
          UUID.randomUUID(), Instant.now(), receiverId1, "제목1", "내용1", NotificationLevel.INFO);
      NotificationDto dto2 = new NotificationDto(
          UUID.randomUUID(), Instant.now(), receiverId2, "제목2", "내용2", NotificationLevel.INFO);
      SseEmitter emitter1 = mock(SseEmitter.class);
      SseEmitter emitter2 = mock(SseEmitter.class);
      given(sseEmitterRepository.findByUserId(receiverId1)).willReturn(Optional.of(emitter1));
      given(sseEmitterRepository.findByUserId(receiverId2)).willReturn(Optional.of(emitter2));

      // when
      sseService.send(List.of(dto1, dto2), "notifications");

      // then
      verify(emitter1).send(any(SseEmitter.SseEventBuilder.class));
      verify(emitter2).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("수신자의_emitter가_없으면_저장만_하고_전송은_하지_않는다")
    void 수신자의_emitter가_없으면_저장만_하고_전송은_하지_않는다() {
      // given
      UUID receiverId = UUID.randomUUID();
      NotificationDto dto = new NotificationDto(
          UUID.randomUUID(), Instant.now(), receiverId, "제목", "내용", NotificationLevel.INFO);
      given(sseEmitterRepository.findByUserId(receiverId)).willReturn(Optional.empty());

      // when
      sseService.send(List.of(dto), "notifications");

      // then
      verify(sseMessageRepository).save(any(SseMessage.class));
    }
  }

  @Nested
  @DisplayName("connect/send 동시성")
  class ConnectSendConcurrency {

    private ExecutorService executor;

    @BeforeEach
    void setUpExecutor() {
      executor = Executors.newFixedThreadPool(2);
    }

    @AfterEach
    void tearDownExecutor() {
      executor.shutdown();
    }

    @Test
    @DisplayName("connect가_emitter_등록과_스냅샷_확정을_진행하는_동안_같은_유저의_send는_대기했다가_그_이후에_진행된다")
    void connect가_emitter_등록과_스냅샷_확정을_진행하는_동안_같은_유저의_send는_대기했다가_그_이후에_진행된다()
        throws Exception {
      // given — snapshotAt 조회(getLatestCreatedAt) 시점에 connect를 멈춰 세워
      // "emitter 등록 ~ 스냅샷 확정" 구간을 하나의 임계구역으로 붙잡아 둔다
      UUID userId = UUID.randomUUID();
      CountDownLatch connectHoldingCriticalSection = new CountDownLatch(1);
      CountDownLatch releaseConnect = new CountDownLatch(1);
      given(sseMessageRepository.getLatestCreatedAt()).willAnswer(invocation -> {
        connectHoldingCriticalSection.countDown();
        releaseConnect.await();
        return null;
      });
      given(sseMessageRepository.findAllAfter(any(), any())).willReturn(List.of());

      try (MockedConstruction<SseEmitter> mocked = mockConstruction(SseEmitter.class)) {
        // when
        Future<SseEmitter> connectFuture = executor.submit(() -> sseService.connect(userId, null));
        connectHoldingCriticalSection.await();

        NotificationDto dto = new NotificationDto(
            UUID.randomUUID(), Instant.now(), userId, "제목", "내용", NotificationLevel.INFO);
        Future<?> sendFuture = executor.submit(() -> sseService.send(List.of(dto), "notifications"));

        // then — connect가 임계구역을 쥐고 있는 동안에는 send가 메시지를 저장하지 못하고 대기해야 한다
        Thread.sleep(200);
        verify(sseMessageRepository, never()).save(any(SseMessage.class));

        // when — connect를 마저 진행시키면
        releaseConnect.countDown();
        connectFuture.get();
        sendFuture.get();

        // then — 그제서야 send가 진행된다(emitter 등록·스냅샷 확정과 겹치지 않음)
        verify(sseMessageRepository).save(any(SseMessage.class));
      }
    }
  }
}