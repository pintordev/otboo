package com.sprint.mission.otboo.global.sse;

import static org.assertj.core.api.Assertions.assertThat;

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
}