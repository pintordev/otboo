package com.sprint.mission.otboo.domain.weathernotification.sse.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.ConstructorPropertiesArbitraryIntrospector;
import com.sprint.mission.otboo.domain.weathernotification.notification.dto.NotificationDto;
import com.sprint.mission.otboo.domain.weathernotification.sse.config.SseConfig;
import com.sprint.mission.otboo.domain.weathernotification.sse.dto.EmitterConnection;
import com.sprint.mission.otboo.domain.weathernotification.sse.dto.SseMessage;
import com.sprint.mission.otboo.domain.weathernotification.sse.repository.SseEmitterRepository;
import com.sprint.mission.otboo.domain.weathernotification.sse.repository.SseMessageRepository;
import java.io.IOException;
import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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
  private final FixtureMonkey fm = FixtureMonkey.builder()
      .objectIntrospector(ConstructorPropertiesArbitraryIntrospector.INSTANCE)
      .build();
  private static final Instant FIXED_NOW = Instant.parse("2026-01-01T00:00:00Z");
  private final Clock clock = Clock.fixed(FIXED_NOW, ZoneOffset.UTC);

  @BeforeEach
  void setUp() {
    sseService = new SseService(sseEmitterRepository, sseMessageRepository, stringRedisTemplate,
        objectMapper, clock);
  }

  @Nested
  @DisplayName("connect")
  class Connect {

    @Test
    @DisplayName("emitter를_생성해_repository에_등록하고_생성한_emitter를_반환한다")
    void emitter를_생성해_repository에_등록하고_생성한_emitter를_반환한다() {
      // given
      UUID userId = UUID.randomUUID();
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
    @DisplayName("lastEventId가_없으면_재생_버퍼_조회_없이_스냅샷을_null로_둔다")
    void lastEventId가_없으면_재생_버퍼_조회_없이_스냅샷을_null로_둔다() {
      // given — 최초 연결이라 재생 대상 자체가 없다
      UUID userId = UUID.randomUUID();

      try (MockedConstruction<SseEmitter> mocked = mockConstruction(SseEmitter.class)) {
        // when
        sseService.connect(userId, null);

        // then — 재생이 일어나지 않으므로 스냅샷도 null이어야 실시간 전달이 스킵되지 않는다
        SseEmitter createdEmitter = mocked.constructed().get(0);
        verify(sseMessageRepository, never()).getLatestSequence();
        verify(sseEmitterRepository).save(userId, createdEmitter, null);
      }
    }

    @Test
    @DisplayName("ping_전송에_실패하면_유실_이벤트_재생을_스킵한다")
    void ping_전송에_실패하면_유실_이벤트_재생을_스킵한다() throws IOException {
      // given
      UUID userId = UUID.randomUUID();
      UUID lastEventId = UUID.randomUUID();
      given(sseMessageRepository.getLatestSequence()).willReturn(null);

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
      SseMessage message1 = new SseMessage(Set.of(userId), "notifications", "payload1",
          Instant.now()).withSeq(1L);
      SseMessage message2 = new SseMessage(Set.of(userId), "notifications", "payload2",
          Instant.now()).withSeq(2L);
      given(sseMessageRepository.getLatestSequence()).willReturn(2L);
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
      SseMessage message1 = new SseMessage(Set.of(userId), "notifications", "payload1",
          Instant.now()).withSeq(1L);
      SseMessage message2 = new SseMessage(Set.of(userId), "notifications", "payload2",
          Instant.now()).withSeq(2L);
      SseMessage message3 = new SseMessage(Set.of(userId), "notifications", "payload3",
          Instant.now()).withSeq(3L);
      given(sseMessageRepository.getLatestSequence()).willReturn(2L);
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
      // given — 전역 최신 이벤트(스냅샷 근거)는 다른 사용자 대상이라 이 사용자의 missed 목록엔 존재하지 않는다
      UUID userId = UUID.randomUUID();
      UUID lastEventId = UUID.randomUUID();
      SseMessage message1 = new SseMessage(Set.of(userId), "notifications", "payload1",
          Instant.now()).withSeq(1L);
      SseMessage message2 = new SseMessage(Set.of(userId), "notifications", "payload2",
          Instant.now()).withSeq(3L);
      given(sseMessageRepository.getLatestSequence()).willReturn(2L);
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

    @Test
    @DisplayName("기존_연결의_complete_호출은_락_해제_후에_일어난다")
    void 기존_연결의_complete_호출은_락_해제_후에_일어난다() {
      // given
      UUID userId = UUID.randomUUID();
      SseEmitter previousEmitter = mock(SseEmitter.class);
      List<String> callOrder = new ArrayList<>();
      doAnswer(inv -> {
        callOrder.add("complete");
        return null;
      }).when(previousEmitter).complete();
      given(sseEmitterRepository.save(eq(userId), any(), isNull()))
          .willAnswer(inv -> {
            callOrder.add("save-returned");
            return Optional.of(new EmitterConnection(previousEmitter, null));
          });
      given(sseMessageRepository.findAllAfter(any(), any())).willReturn(List.of());

      try (MockedConstruction<SseEmitter> mocked = mockConstruction(SseEmitter.class)) {
        // when
        sseService.connect(userId, null);

        assertThat(mocked.constructed()).hasSize(1);
      }

      // then - "save 반환"이 먼저, previousEmitter.complete() 호출은 그 뒤(락 밖)여야 한다
      assertThat(callOrder).containsExactly("save-returned", "complete");
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
      NotificationDto dto = fm.giveMeBuilder(NotificationDto.class)
          .set("receiverId", receiverId)
          .sample();

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
      NotificationDto dto1 = fm.giveMeBuilder(NotificationDto.class)
          .set("receiverId", receiverId1)
          .sample();
      NotificationDto dto2 = fm.giveMeBuilder(NotificationDto.class)
          .set("receiverId", receiverId2)
          .sample();

      // when
      sseService.send(List.of(dto1, dto2), "notifications");

      // then
      verify(sseMessageRepository, times(2)).save(any(SseMessage.class));
      verify(stringRedisTemplate, times(2)).convertAndSend(eq(SseConfig.SSE_CHANNEL), anyString());
    }

    @Test
    @DisplayName("한_수신자_저장이_실패해도_나머지_수신자는_계속_처리된다")
    void 한_수신자_저장이_실패해도_나머지_수신자는_계속_처리된다() {
      // given
      NotificationDto dto1 = fm.giveMeBuilder(NotificationDto.class)
          .set("receiverId", UUID.randomUUID())
          .sample();
      NotificationDto dto2 = fm.giveMeBuilder(NotificationDto.class)
          .set("receiverId", UUID.randomUUID())
          .sample();
      NotificationDto dto3 = fm.giveMeBuilder(NotificationDto.class)
          .set("receiverId", UUID.randomUUID())
          .sample();
      given(sseMessageRepository.save(any()))
          .willReturn(1L)
          .willThrow(new RuntimeException("저장 실패"))
          .willReturn(3L);

      // when
      List<UUID> delivered = sseService.send(List.of(dto1, dto2, dto3), "notifications");

      // then - 2번째가 실패해도 3번째까지 시도돼야 하고, 반환값은 성공한 알림 id만 담는다
      verify(sseMessageRepository, times(3)).save(any());
      verify(stringRedisTemplate, times(2)).convertAndSend(eq(SseConfig.SSE_CHANNEL), anyString());
      assertThat(delivered).containsExactly(dto1.id(), dto3.id());
    }

    @Test
    @DisplayName("주입된_Clock의_시각으로_메시지를_생성한다")
    void 주입된_Clock의_시각으로_메시지를_생성한다() {
      // given
      NotificationDto dto = fm.giveMeBuilder(NotificationDto.class)
          .set("receiverId", UUID.randomUUID())
          .sample();
      ArgumentCaptor<SseMessage> captor = ArgumentCaptor.forClass(SseMessage.class);

      // when
      sseService.send(List.of(dto), "notifications");

      // then - 실제 벽시계가 아니라 주입된 Clock(FIXED_NOW)의 시각을 사용해야 한다
      verify(sseMessageRepository).save(captor.capture());
      assertThat(captor.getValue().createdAt()).isEqualTo(FIXED_NOW);
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
      SseMessage message =
          new SseMessage(Set.of(userId), "notifications", "payload", Instant.now());
      SseEmitter emitter = mock(SseEmitter.class);
      given(sseEmitterRepository.findSnapshotSeq(userId)).willReturn(Optional.empty());
      given(sseEmitterRepository.findByUserId(userId)).willReturn(Optional.of(emitter));

      // when
      sseService.deliverLocally(message);

      // then
      verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("seq가_없는_구버전_메시지도_전달한다")
    void seq가_없는_구버전_메시지도_전달한다() throws IOException {
      // given - 롤링 배포 중 구버전 인스턴스가 발행한, seq 필드가 없는 메시지와의 호환성
      UUID userId = UUID.randomUUID();
      SseMessage legacyMessage =
          new SseMessage(Set.of(userId), "notifications", "payload", Instant.now()); // seq=null
      SseEmitter emitter = mock(SseEmitter.class);
      given(sseEmitterRepository.findSnapshotSeq(userId)).willReturn(Optional.of(5L));
      given(sseEmitterRepository.findByUserId(userId)).willReturn(Optional.of(emitter));

      // when
      sseService.deliverLocally(legacyMessage);

      // then - seq를 알 수 없으면 이미 재생됐다고 판단하지 않고 그대로 전달한다(NPE로 조용히
      // 폐기되면 안 됨)
      verify(emitter).send(any(SseEmitter.SseEventBuilder.class));
    }

    @Test
    @DisplayName("재생_스냅샷_이전_메시지는_중복_전송하지_않는다")
    void 재생_스냅샷_이전_메시지는_중복_전송하지_않는다() {
      // given — save()와 로컬 전송 사이 Pub/Sub 왕복 구간에 connect()가 끼어들어
      // 이미 재생으로 처리된 것과 같은 메시지가 뒤늦게 도착하는 상황을 재현
      UUID userId = UUID.randomUUID();
      SseMessage alreadyReplayed =
          new SseMessage(Set.of(userId), "notifications", "payload", Instant.now())
              .withSeq(1L);
      given(sseEmitterRepository.findSnapshotSeq(userId)).willReturn(Optional.of(2L));

      // when
      sseService.deliverLocally(alreadyReplayed);

      // then
      verify(sseEmitterRepository, never()).findByUserId(userId);
    }

    @Test
    @DisplayName("스냅샷을_만든_메시지_자신은_경계값이라도_중복_전송하지_않는다")
    void 스냅샷을_만든_메시지_자신은_경계값이라도_중복_전송하지_않는다() {
      // given — snapshotSeq가 이 메시지 자신의 seq와 같은 경계값(<=)인 경우
      UUID userId = UUID.randomUUID();
      SseMessage boundaryMessage =
          new SseMessage(Set.of(userId), "notifications", "payload", Instant.now())
              .withSeq(5L);
      given(sseEmitterRepository.findSnapshotSeq(userId)).willReturn(Optional.of(5L));

      // when
      sseService.deliverLocally(boundaryMessage);

      // then — connect() 재생으로 이미 처리된 것으로 간주해 로컬 전송하지 않는다
      verify(sseEmitterRepository, never()).findByUserId(userId);
    }

    @Test
    @DisplayName("emitter_전송_중에도_같은_유저의_다음_메시지_스냅샷_판정은_막히지_않는다")
    void emitter_전송_중에도_같은_유저의_다음_메시지_스냅샷_판정은_막히지_않는다() throws Exception {
      // given — message1의 emitter.send()를 인위적으로 블로킹시켜, 그 사이 message2의
      // 스냅샷 판정(findSnapshotAt)이 락에 막히지 않고 진행되는지 확인한다
      UUID userId = UUID.randomUUID();
      SseMessage message1 =
          new SseMessage(Set.of(userId), "notifications", "payload1", Instant.now());
      SseMessage message2 =
          new SseMessage(Set.of(userId), "notifications", "payload2", Instant.now());
      SseEmitter blockingEmitter = mock(SseEmitter.class);
      SseEmitter fastEmitter = mock(SseEmitter.class);
      CountDownLatch sendStarted = new CountDownLatch(1);
      CountDownLatch releaseSend = new CountDownLatch(1);
      given(sseEmitterRepository.findSnapshotSeq(userId)).willReturn(Optional.empty());
      given(sseEmitterRepository.findByUserId(userId))
          .willReturn(Optional.of(blockingEmitter), Optional.of(fastEmitter));
      doAnswer(invocation -> {
        sendStarted.countDown();
        releaseSend.await();
        return null;
      }).when(blockingEmitter).send(any(SseEmitter.SseEventBuilder.class));

      ExecutorService executor = Executors.newFixedThreadPool(2);
      try {
        Future<?> future1 = executor.submit(() -> sseService.deliverLocally(message1));
        assertThat(sendStarted.await(2, TimeUnit.SECONDS)).isTrue();

        // when — message1의 emitter.send()가 아직 블로킹 중인 상태에서 message2를 처리
        Future<?> future2 = executor.submit(() -> sseService.deliverLocally(message2));

        // then — 락이 send() 전에 이미 풀렸다면 타임아웃 없이 바로 완료된다
        future2.get(2, TimeUnit.SECONDS);

        releaseSend.countDown();
        future1.get(2, TimeUnit.SECONDS);
      } finally {
        releaseSend.countDown();
        executor.shutdownNow();
        assertThat(executor.awaitTermination(2, TimeUnit.SECONDS)).isTrue();
      }
    }

    @Test
    @DisplayName("한_수신자_처리가_실패해도_예외를_전파하지_않고_나머지_수신자는_계속_전달한다")
    void 한_수신자_처리가_실패해도_나머지_수신자는_계속_전달한다() throws IOException {
      // given
      UUID failingReceiverId = UUID.randomUUID();
      UUID okReceiverId = UUID.randomUUID();
      SseMessage message = new SseMessage(
          Set.of(failingReceiverId, okReceiverId), "notifications", "payload", Instant.now());
      SseEmitter okEmitter = mock(SseEmitter.class);
      given(sseEmitterRepository.findSnapshotSeq(failingReceiverId))
          .willThrow(new RuntimeException("redis 장애"));
      given(sseEmitterRepository.findSnapshotSeq(okReceiverId)).willReturn(Optional.empty());
      given(sseEmitterRepository.findByUserId(okReceiverId)).willReturn(Optional.of(okEmitter));

      // when & then
      assertThatCode(() -> sseService.deliverLocally(message)).doesNotThrowAnyException();
      verify(okEmitter).send(any(SseEmitter.SseEventBuilder.class));
    }
  }

  @Nested
  @DisplayName("connectionLocks 스트라이핑")
  class ConnectionLockStriping {

    @Test
    @DisplayName("같은_userId는_항상_같은_락을_반환하고_userId가_다양하면_여러_락으로_분산된다")
    void 같은_userId는_항상_같은_락을_반환하고_userId가_다양하면_여러_락으로_분산된다()
        throws Exception {
      // given
      Method lockForMethod = SseService.class.getDeclaredMethod("lockFor", UUID.class);
      lockForMethod.setAccessible(true);
      UUID fixedUserId = UUID.randomUUID();
      Set<Object> distinctLocks = new HashSet<>();

      // when
      Object first = lockForMethod.invoke(sseService, fixedUserId);
      Object second = lockForMethod.invoke(sseService, fixedUserId);
      for (int i = 0; i < 1000; i++) {
        distinctLocks.add(lockForMethod.invoke(sseService, UUID.randomUUID()));
      }

      // then
      assertThat(first).isSameAs(second);
      assertThat(distinctLocks.size()).isGreaterThan(200);
    }
  }
}