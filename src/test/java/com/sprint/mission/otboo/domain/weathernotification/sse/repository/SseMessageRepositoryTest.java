package com.sprint.mission.otboo.domain.weathernotification.sse.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.sprint.mission.otboo.domain.weathernotification.sse.dto.SseMessage;
import com.sprint.mission.otboo.domain.weathernotification.sse.properties.SseReplayBufferProperties;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SseMessageRepositoryTest {

  private SseMessageRepository sseMessageRepository;

  @BeforeEach
  void setUp() {
    sseMessageRepository = new SseMessageRepository(
        Clock.systemUTC(), new SseReplayBufferProperties(10));
  }

  @Nested
  @DisplayName("저장 / 최신 이벤트 생성 시각 조회")
  class SaveAndGetLatestCreatedAt {

    @Test
    @DisplayName("저장하면_메시지의_id를_반환한다")
    void 저장하면_메시지의_id를_반환한다() {
      // given
      SseMessage message = new SseMessage(Set.of(UUID.randomUUID()), "notifications", "payload");

      // when
      UUID savedId = sseMessageRepository.save(message);

      // then
      assertThat(savedId).isEqualTo(message.id());
    }

    @Test
    @DisplayName("저장한_메시지가_없으면_getLatestCreatedAt은_null을_반환한다")
    void 저장한_메시지가_없으면_getLatestCreatedAt은_null을_반환한다() {
      assertThat(sseMessageRepository.getLatestCreatedAt()).isNull();
    }

    @Test
    @DisplayName("저장할_때마다_getLatestCreatedAt은_가장_최근_메시지의_생성_시각을_반환한다")
    void 저장할_때마다_getLatestCreatedAt은_가장_최근_메시지의_생성_시각을_반환한다() {
      // given
      SseMessage first = new SseMessage(Set.of(UUID.randomUUID()), "notifications", "payload1");
      SseMessage second = new SseMessage(Set.of(UUID.randomUUID()), "notifications", "payload2");

      // when
      sseMessageRepository.save(first);
      sseMessageRepository.save(second);

      // then
      assertThat(sseMessageRepository.getLatestCreatedAt()).isEqualTo(second.createdAt());
    }
  }

  @Nested
  @DisplayName("findAllAfter")
  class FindAllAfter {

    @Test
    @DisplayName("lastEventId가_null이면_빈_리스트를_반환한다")
    void lastEventId가_null이면_빈_리스트를_반환한다() {
      // given
      UUID userId = UUID.randomUUID();
      sseMessageRepository.save(new SseMessage(Set.of(userId), "notifications", "payload"));

      // when
      List<SseMessage> result = sseMessageRepository.findAllAfter(null, userId);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("존재하는_lastEventId_이후에_저장된_메시지_중_해당_유저_대상만_반환한다")
    void 존재하는_lastEventId_이후에_저장된_메시지_중_해당_유저_대상만_반환한다() {
      // given
      UUID userId = UUID.randomUUID();
      UUID otherUserId = UUID.randomUUID();
      SseMessage before = new SseMessage(Set.of(userId), "notifications", "before");
      SseMessage afterForOther = new SseMessage(Set.of(otherUserId), "notifications",
          "afterForOther");
      SseMessage afterForUser = new SseMessage(Set.of(userId), "notifications", "afterForUser");
      sseMessageRepository.save(before);
      sseMessageRepository.save(afterForOther);
      sseMessageRepository.save(afterForUser);

      // when
      List<SseMessage> result = sseMessageRepository.findAllAfter(before.id(), userId);

      // then
      assertThat(result).containsExactly(afterForUser);
    }

    @Test
    @DisplayName("lastEventId가_evict되어_큐에_없으면_빈_리스트를_반환한다")
    void lastEventId가_evict되어_큐에_없으면_빈_리스트를_반환한다() {
      // given
      UUID userId = UUID.randomUUID();
      SseMessage message = new SseMessage(Set.of(userId), "notifications", "payload");
      sseMessageRepository.save(message);

      // when — 존재하지 않는(=evict된) lastEventId로 조회
      List<SseMessage> result = sseMessageRepository.findAllAfter(UUID.randomUUID(), userId);

      // then — 못 받은 게 있을 수 있으니 가진 걸 다 돌려주는 대신 빈 리스트로 재전송을 막는다
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("보관 기간 초과 시 eviction")
  class Eviction {

    @Test
    @DisplayName("보관_기간이_지난_메시지는_저장_시점에_제거되고_그_이후_메시지는_유지된다")
    void 보관_기간이_지난_메시지는_저장_시점에_제거되고_그_이후_메시지는_유지된다() {
      // given
      UUID userId = UUID.randomUUID();
      Instant now = Instant.parse("2026-01-01T00:20:00Z");
      Clock fixedClock = Clock.fixed(now, ZoneOffset.UTC);
      SseMessageRepository repository = new SseMessageRepository(
          fixedClock, new SseReplayBufferProperties(10));

      SseMessage expired = new SseMessage(UUID.randomUUID(), Set.of(userId), "notifications",
          "old", now.minus(Duration.ofMinutes(11)));
      SseMessage anchor = new SseMessage(UUID.randomUUID(), Set.of(userId), "notifications",
          "anchor", now.minus(Duration.ofMinutes(6)));
      SseMessage kept = new SseMessage(UUID.randomUUID(), Set.of(userId), "notifications",
          "kept", now.minus(Duration.ofMinutes(1)));

      // when
      repository.save(expired);
      repository.save(anchor);
      repository.save(kept);

      // then — 보관 기간(10분)이 지난 메시지는 제거돼 그 id로 더 이상 조회할 수 없다
      assertThat(repository.findAllAfter(expired.id(), userId)).isEmpty();
      // 보관 기간 내 메시지는 그대로 남아 정상 조회된다
      assertThat(repository.findAllAfter(anchor.id(), userId)).containsExactly(kept);
    }
  }

  @Nested
  @DisplayName("유휴 상태 만료 처리")
  class IdleExpiration {

    @Test
    @DisplayName("저장_없이_유휴_상태로_보관_기간이_지나면_조회_시점에_만료된_메시지가_제거된다")
    void 저장_없이_유휴_상태로_보관_기간이_지나면_조회_시점에_만료된_메시지가_제거된다() {
      // given — save() 시점(t1, t2)엔 둘 다 보관 기간 내라 제거되지 않고, 이후 추가 save 없이
      // 유휴 상태로 보관 기간(10분)을 넘긴 시점(idleNow)에 조회한다
      UUID userId = UUID.randomUUID();
      Instant t1 = Instant.parse("2026-01-01T00:00:01Z");
      Instant t2 = Instant.parse("2026-01-01T00:00:02Z");
      Instant idleNow = t2.plus(Duration.ofMinutes(11));
      Clock clock = mock(Clock.class);
      given(clock.instant()).willReturn(t1, t2, idleNow);
      SseMessageRepository repository = new SseMessageRepository(
          clock, new SseReplayBufferProperties(10));
      SseMessage message1 = new SseMessage(UUID.randomUUID(), Set.of(userId), "notifications",
          "payload1", t1);
      SseMessage message2 = new SseMessage(UUID.randomUUID(), Set.of(userId), "notifications",
          "payload2", t2);

      // when
      repository.save(message1);
      repository.save(message2);

      // then — 유휴 상태로 만료된 뒤라 추가 save 없이도 조회 시점에 제거돼 빈 리스트가 반환된다
      assertThat(repository.findAllAfter(message1.id(), userId)).isEmpty();
      assertThat(repository.getLatestCreatedAt()).isNull();
    }
  }

  @Nested
  @DisplayName("동시성")
  class Concurrency {

    private ExecutorService executor;

    @BeforeEach
    void setUpExecutor() {
      executor = Executors.newFixedThreadPool(20);
    }

    @AfterEach
    void tearDownExecutor() {
      executor.shutdown();
    }

    @Test
    @DisplayName("save가_동시에_들어와도_messages와_eventIdQueue가_어긋나지_않아_유실_없이_전부_조회된다")
    void save가_동시에_들어와도_messages와_eventIdQueue가_어긋나지_않아_유실_없이_전부_조회된다()
        throws Exception {
      int trials = 30;
      int concurrency = 20;

      for (int trial = 0; trial < trials; trial++) {
        // given
        UUID userId = UUID.randomUUID();
        SseMessage seed = new SseMessage(Set.of(userId), "notifications", "seed");
        sseMessageRepository.save(seed);

        CountDownLatch ready = new CountDownLatch(concurrency);
        CountDownLatch start = new CountDownLatch(1);
        List<Callable<Void>> tasks = new ArrayList<>();
        for (int i = 0; i < concurrency; i++) {
          int index = i;
          tasks.add(() -> {
            ready.countDown();
            start.await();
            sseMessageRepository.save(
                new SseMessage(Set.of(userId), "notifications", "payload-" + index));
            return null;
          });
        }

        // when
        List<Future<Void>> futures = new ArrayList<>();
        for (Callable<Void> task : tasks) {
          futures.add(executor.submit(task));
        }
        ready.await();
        start.countDown();
        for (Future<Void> future : futures) {
          future.get();
        }

        // then — messages.put()과 eventIdQueue.addLast() 사이의 창에서 유실되는 메시지가 없어야 한다
        List<SseMessage> result = sseMessageRepository.findAllAfter(seed.id(), userId);
        assertThat(result)
            .as("trial %d: 동시에 저장된 %d건이 하나도 유실되지 않아야 한다", trial, concurrency)
            .hasSize(concurrency);
      }
    }
  }
}