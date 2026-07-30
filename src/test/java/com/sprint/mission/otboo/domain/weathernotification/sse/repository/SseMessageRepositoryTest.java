package com.sprint.mission.otboo.domain.weathernotification.sse.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.domain.weathernotification.sse.dto.SseMessage;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SseMessageRepositoryTest {

  private SseMessageRepository sseMessageRepository;

  @BeforeEach
  void setUp() {
    sseMessageRepository = new SseMessageRepository();
  }

  @Nested
  @DisplayName("저장 / 최신 이벤트 id 조회")
  class SaveAndGetLatestEventId {

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
    @DisplayName("저장한_메시지가_없으면_getLatestEventId는_null을_반환한다")
    void 저장한_메시지가_없으면_getLatestEventId는_null을_반환한다() {
      assertThat(sseMessageRepository.getLatestEventId()).isNull();
    }

    @Test
    @DisplayName("저장할_때마다_getLatestEventId는_가장_최근_메시지의_id를_반환한다")
    void 저장할_때마다_getLatestEventId는_가장_최근_메시지의_id를_반환한다() {
      // given
      SseMessage first = new SseMessage(Set.of(UUID.randomUUID()), "notifications", "payload1");
      SseMessage second = new SseMessage(Set.of(UUID.randomUUID()), "notifications", "payload2");

      // when
      sseMessageRepository.save(first);
      sseMessageRepository.save(second);

      // then
      assertThat(sseMessageRepository.getLatestEventId()).isEqualTo(second.id());
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
    @DisplayName("lastEventId가_큐에_없으면_저장된_메시지_전체를_해당_유저_기준으로_반환한다")
    void lastEventId가_큐에_없으면_저장된_메시지_전체를_해당_유저_기준으로_반환한다() {
      // given
      UUID userId = UUID.randomUUID();
      SseMessage message = new SseMessage(Set.of(userId), "notifications", "payload");
      sseMessageRepository.save(message);

      // when
      List<SseMessage> result = sseMessageRepository.findAllAfter(UUID.randomUUID(), userId);

      // then
      assertThat(result).containsExactly(message);
    }
  }

  @Nested
  @DisplayName("최대 크기 초과 시 eviction")
  class Eviction {

    @Test
    @DisplayName("최대_크기를_초과하면_가장_오래된_메시지부터_제거해_최대_1000건만_유지한다")
    void 최대_크기를_초과하면_가장_오래된_메시지부터_제거해_최대_1000건만_유지한다() {
      // given
      UUID userId = UUID.randomUUID();
      for (int i = 0; i < 1001; i++) {
        sseMessageRepository.save(new SseMessage(Set.of(userId), "notifications", "payload-" + i));
      }

      // when — 존재하지 않는 lastEventId를 넘겨 큐 전체를 재생 대상으로 조회
      List<SseMessage> result = sseMessageRepository.findAllAfter(UUID.randomUUID(), userId);

      // then
      assertThat(result).hasSize(1000);
    }
  }
}