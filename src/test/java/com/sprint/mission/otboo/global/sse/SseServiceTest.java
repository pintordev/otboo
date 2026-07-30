package com.sprint.mission.otboo.global.sse;

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

import java.io.IOException;
import java.util.List;
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
            given(sseMessageRepository.getLatestEventId()).willReturn(null);
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
            given(sseMessageRepository.getLatestEventId()).willReturn(null);

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
            SseMessage message1 = new SseMessage(Set.of(userId), "notifications", "payload1");
            SseMessage message2 = new SseMessage(Set.of(userId), "notifications", "payload2");
            given(sseMessageRepository.getLatestEventId()).willReturn(message2.id());
            given(sseMessageRepository.findAllAfter(lastEventId, userId)).willReturn(List.of(message1, message2));

            try (MockedConstruction<SseEmitter> mocked = mockConstruction(SseEmitter.class)) {
                // when
                sseService.connect(userId, lastEventId);

                // then — ping 1회 + 재생 2회
                SseEmitter createdEmitter = mocked.constructed().get(0);
                verify(createdEmitter, times(3)).send(any(SseEmitter.SseEventBuilder.class));
            }
        }

        @Test
        @DisplayName("재생_중_연결_시점_최신_이벤트_id에_도달하면_그_이후는_재생하지_않는다")
        void 재생_중_연결_시점_최신_이벤트_id에_도달하면_그_이후는_재생하지_않는다() throws IOException {
            // given
            UUID userId = UUID.randomUUID();
            UUID lastEventId = UUID.randomUUID();
            SseMessage message1 = new SseMessage(Set.of(userId), "notifications", "payload1");
            SseMessage message2 = new SseMessage(Set.of(userId), "notifications", "payload2");
            SseMessage message3 = new SseMessage(Set.of(userId), "notifications", "payload3");
            given(sseMessageRepository.getLatestEventId()).willReturn(message2.id());
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
    }

    @Nested
    @DisplayName("disconnectAll")
    class DisconnectAll {

        @Test
        @DisplayName("해당_유저의_emitter가_있으면_complete를_호출한다")
        void 해당_유저의_emitter가_있으면_complete를_호출한다() {
            // given
            UUID userId = UUID.randomUUID();
            SseEmitter emitter = mock(SseEmitter.class);
            given(sseEmitterRepository.findByUserId(userId)).willReturn(Optional.of(emitter));

            // when
            sseService.disconnectAll(userId);

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
            sseService.disconnectAll(userId);
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
            sseService.disconnectAll(targetUserId);

            // then
            verify(targetEmitter).complete();
            verify(otherEmitter, never()).complete();
            verify(sseEmitterRepository, never()).findByUserId(otherUserId);
        }
    }
}