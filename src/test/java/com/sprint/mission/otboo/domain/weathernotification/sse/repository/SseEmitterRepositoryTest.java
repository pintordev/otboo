package com.sprint.mission.otboo.domain.weathernotification.sse.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

class SseEmitterRepositoryTest {

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
      sseEmitterRepository.save(userId, emitter);
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
      sseEmitterRepository.save(userId, previous);

      // when
      sseEmitterRepository.save(userId, next);

      // then
      assertThat(sseEmitterRepository.findByUserId(userId)).contains(next);
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
      sseEmitterRepository.save(userId, emitter);

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
      sseEmitterRepository.save(userId, previous);
      sseEmitterRepository.save(userId, next);

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
      sseEmitterRepository.save(userId1, emitter1);
      sseEmitterRepository.save(userId2, emitter2);

      // when & then
      assertThat(sseEmitterRepository.findAll())
          .containsEntry(userId1, emitter1)
          .containsEntry(userId2, emitter2);
    }
  }
}