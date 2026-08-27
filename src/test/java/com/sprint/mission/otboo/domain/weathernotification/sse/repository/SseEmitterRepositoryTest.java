package com.sprint.mission.otboo.domain.weathernotification.sse.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.sprint.mission.otboo.domain.weathernotification.sse.dto.EmitterConnection;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseEmitterRepositoryTest {

  private static final Instant SNAPSHOT_AT = Instant.now();

  private SseEmitterRepository sseEmitterRepository;

  @BeforeEach
  void setUp() {
    sseEmitterRepository = new SseEmitterRepository();
  }

  @Nested
  @DisplayName("저장 / 조회")
  class SaveAndFind {

    @Test
    @DisplayName("저장한_emitter를_findByUserId로_그대로_조회할_수_있다")
    void 저장한_emitter를_findByUserId로_그대로_조회할_수_있다() {
      // given
      UUID userId = UUID.randomUUID();
      SseEmitter emitter = new SseEmitter();

      // when
      sseEmitterRepository.save(userId, emitter, SNAPSHOT_AT);
      Optional<SseEmitter> found = sseEmitterRepository.findByUserId(userId);

      // then
      assertThat(found).isPresent();
      assertThat(found.get()).isSameAs(emitter);
    }

    @Test
    @DisplayName("존재하지_않는_유저를_조회하면_빈_Optional을_반환한다")
    void 존재하지_않는_유저를_조회하면_빈_Optional을_반환한다() {
      Optional<SseEmitter> found = sseEmitterRepository.findByUserId(UUID.randomUUID());

      assertThat(found).isEmpty();
    }

    @Test
    @DisplayName("같은_userId로_새_emitter를_저장하면_이전_emitter를_대체한다")
    void 같은_userId로_새_emitter를_저장하면_이전_emitter를_대체한다() {
      // given
      UUID userId = UUID.randomUUID();
      SseEmitter previous = new SseEmitter();
      SseEmitter next = new SseEmitter();
      sseEmitterRepository.save(userId, previous, SNAPSHOT_AT);

      // when
      sseEmitterRepository.save(userId, next, SNAPSHOT_AT);

      // then
      assertThat(sseEmitterRepository.findByUserId(userId)).contains(next);
    }
  }

  @Nested
  @DisplayName("재연결 시 이전 연결 반환")
  class ReplaceOnReconnect {

    @Test
    @DisplayName("같은_userId로_새_emitter를_저장하면_이전_연결을_Optional로_반환한다")
    void 같은_userId로_새_emitter를_저장하면_이전_연결을_Optional로_반환한다() {
      // given
      UUID userId = UUID.randomUUID();
      SseEmitter previous = mock(SseEmitter.class);
      SseEmitter next = new SseEmitter();
      sseEmitterRepository.save(userId, previous, SNAPSHOT_AT);

      // when
      Optional<EmitterConnection> result = sseEmitterRepository.save(userId, next, SNAPSHOT_AT);

      // then — complete() 호출 여부는 더 이상 여기서 검증하지 않는다(호출부 SseService.connect()의 책임)
      assertThat(result).isPresent();
      assertThat(result.get().emitter()).isSameAs(previous);
    }

    @Test
    @DisplayName("최초_저장이면_빈_Optional을_반환한다")
    void 최초_저장이면_빈_Optional을_반환한다() {
      // given
      UUID userId = UUID.randomUUID();

      // when
      Optional<EmitterConnection> result =
          sseEmitterRepository.save(userId, mock(SseEmitter.class), SNAPSHOT_AT);

      // then
      assertThat(result).isEmpty();
    }
  }

  @Nested
  @DisplayName("제거")
  class Remove {

    @Test
    @DisplayName("전달받은_emitter가_현재_저장된_것과_같은_참조면_제거한다")
    void 전달받은_emitter가_현재_저장된_것과_같은_참조면_제거한다() {
      // given
      UUID userId = UUID.randomUUID();
      SseEmitter emitter = new SseEmitter();
      sseEmitterRepository.save(userId, emitter, SNAPSHOT_AT);

      // when
      sseEmitterRepository.remove(userId, emitter);

      // then
      assertThat(sseEmitterRepository.findByUserId(userId)).isEmpty();
    }

    @Test
    @DisplayName("전달받은_emitter가_이미_새_emitter로_교체된_뒤라면_아무_것도_하지_않는다")
    void 전달받은_emitter가_이미_새_emitter로_교체된_뒤라면_아무_것도_하지_않는다() {
      // given
      UUID userId = UUID.randomUUID();
      SseEmitter previous = new SseEmitter();
      SseEmitter next = new SseEmitter();
      sseEmitterRepository.save(userId, previous, SNAPSHOT_AT);
      sseEmitterRepository.save(userId, next, SNAPSHOT_AT);

      // when
      sseEmitterRepository.remove(userId, previous);

      // then
      assertThat(sseEmitterRepository.findByUserId(userId)).contains(next);
    }
  }

  @Nested
  @DisplayName("전체 조회")
  class FindAll {

    @Test
    @DisplayName("저장된_모든_emitter를_userId_기준_맵으로_반환한다")
    void 저장된_모든_emitter를_userId_기준_맵으로_반환한다() {
      // given
      UUID userId1 = UUID.randomUUID();
      UUID userId2 = UUID.randomUUID();
      SseEmitter emitter1 = new SseEmitter();
      SseEmitter emitter2 = new SseEmitter();
      sseEmitterRepository.save(userId1, emitter1, SNAPSHOT_AT);
      sseEmitterRepository.save(userId2, emitter2, SNAPSHOT_AT);

      // when & then
      assertThat(sseEmitterRepository.findAll())
          .containsEntry(userId1, emitter1)
          .containsEntry(userId2, emitter2);
    }
  }

  @Nested
  @DisplayName("재생 스냅샷 조회")
  class FindSnapshotAt {

    @Test
    @DisplayName("저장한_스냅샷_시각을_그대로_조회할_수_있다")
    void 저장한_스냅샷_시각을_그대로_조회할_수_있다() {
      // given
      UUID userId = UUID.randomUUID();
      SseEmitter emitter = new SseEmitter();

      // when
      sseEmitterRepository.save(userId, emitter, SNAPSHOT_AT);

      // then
      assertThat(sseEmitterRepository.findSnapshotAt(userId)).contains(SNAPSHOT_AT);
    }

    @Test
    @DisplayName("존재하지_않는_유저를_조회하면_빈_Optional을_반환한다")
    void 존재하지_않는_유저를_조회하면_빈_Optional을_반환한다() {
      assertThat(sseEmitterRepository.findSnapshotAt(UUID.randomUUID())).isEmpty();
    }

    @Test
    @DisplayName("스냅샷_시각이_null로_저장되면_빈_Optional을_반환한다")
    void 스냅샷_시각이_null로_저장되면_빈_Optional을_반환한다() {
      // given
      UUID userId = UUID.randomUUID();
      SseEmitter emitter = new SseEmitter();

      // when
      sseEmitterRepository.save(userId, emitter, null);

      // then
      assertThat(sseEmitterRepository.findSnapshotAt(userId)).isEmpty();
    }
  }
}