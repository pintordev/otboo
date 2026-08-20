package com.sprint.mission.otboo.domain.weathernotification.sse.listener;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sprint.mission.otboo.domain.weathernotification.sse.dto.SseMessage;
import com.sprint.mission.otboo.domain.weathernotification.sse.service.SseService;
import java.nio.charset.StandardCharsets;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;
import tools.jackson.databind.ObjectMapper;

class SseRedisMessageListenerTest {

  private final ObjectMapper objectMapper = new ObjectMapper();
  private final SseService sseService = mock(SseService.class);
  private final SseRedisMessageListener listener =
      new SseRedisMessageListener(objectMapper, sseService);

  @Nested
  @DisplayName("메시지 수신")
  class OnMessage {

    @Test
    @DisplayName("역직렬화한 메시지를 SseService로 로컬 전달한다")
    void 역직렬화한_메시지를_SseService로_로컬_전달한다() throws Exception {
      // given
      SseMessage sseMessage = new SseMessage(Set.of(UUID.randomUUID()), "notifications", "payload");
      byte[] body = objectMapper.writeValueAsBytes(sseMessage);
      Message message = new DefaultMessage("sse:notifications".getBytes(), body);

      // when
      listener.onMessage(message, null);

      // then
      verify(sseService).deliverLocally(sseMessage);
    }

    @Test
    @DisplayName("역직렬화에 실패해도 예외를 전파하지 않고 로컬 전달을 호출하지 않는다")
    void 역직렬화에_실패해도_예외를_전파하지_않는다() {
      // given
      Message message = new DefaultMessage("sse:notifications".getBytes(),
          "not-valid-json".getBytes(StandardCharsets.UTF_8));

      // when & then
      assertThatCode(() -> listener.onMessage(message, null)).doesNotThrowAnyException();
      verify(sseService, never()).deliverLocally(any());
    }

    @Test
    @DisplayName("deliverLocally가 실패해도 예외를 전파하지 않는다")
    void deliverLocally가_실패해도_예외를_전파하지_않는다() throws Exception {
      // given
      SseMessage sseMessage = new SseMessage(Set.of(UUID.randomUUID()), "notifications", "payload");
      byte[] body = objectMapper.writeValueAsBytes(sseMessage);
      Message message = new DefaultMessage("sse:notifications".getBytes(), body);
      willThrow(new RuntimeException("전달 실패")).given(sseService).deliverLocally(sseMessage);

      // when & then
      assertThatCode(() -> listener.onMessage(message, null)).doesNotThrowAnyException();
    }
  }
}