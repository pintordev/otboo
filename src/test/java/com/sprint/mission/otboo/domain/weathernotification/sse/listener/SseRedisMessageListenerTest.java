package com.sprint.mission.otboo.domain.weathernotification.sse.listener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.ConstructorPropertiesArbitraryIntrospector;
import com.sprint.mission.otboo.domain.weathernotification.notification.dto.NotificationDto;
import com.sprint.mission.otboo.domain.weathernotification.sse.dto.SseMessage;
import com.sprint.mission.otboo.domain.weathernotification.sse.service.SseService;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.data.redis.connection.DefaultMessage;
import org.springframework.data.redis.connection.Message;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

class SseRedisMessageListenerTest {

  private final FixtureMonkey fm = FixtureMonkey.builder()
      .objectIntrospector(ConstructorPropertiesArbitraryIntrospector.INSTANCE)
      .build();
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
      SseMessage sseMessage = new SseMessage(Set.of(UUID.randomUUID()), "notifications", "payload",
          Instant.now());
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
      SseMessage sseMessage = new SseMessage(Set.of(UUID.randomUUID()), "notifications", "payload",
          Instant.now());
      byte[] body = objectMapper.writeValueAsBytes(sseMessage);
      Message message = new DefaultMessage("sse:notifications".getBytes(), body);
      willThrow(new RuntimeException("전달 실패")).given(sseService).deliverLocally(sseMessage);

      // when & then
      assertThatCode(() -> listener.onMessage(message, null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("data가 NotificationDto여도 왕복 후 JSON 구조가 그대로 유지된다")
    void data가_NotificationDto여도_왕복_후_JSON_구조가_유지된다() throws Exception {
      // given
      NotificationDto dto = fm.giveMeBuilder(NotificationDto.class)
          .set("receiverId", UUID.randomUUID())
          .sample();
      SseMessage sseMessage = new SseMessage(Set.of(dto.receiverId()), "notifications", dto,
          Instant.now());
      byte[] body = objectMapper.writeValueAsBytes(sseMessage);
      Message message = new DefaultMessage("sse:notifications".getBytes(), body);
      ArgumentCaptor<SseMessage> captor = ArgumentCaptor.forClass(SseMessage.class);

      // when
      listener.onMessage(message, null);

      // then — 역직렬화 후 data는 타입 정보가 없어 LinkedHashMap이 되지만, emitter가
      // 이를 다시 JSON으로 직렬화하므로 클라이언트가 받는 JSON 구조는 원본과 동일해야 한다
      verify(sseService).deliverLocally(captor.capture());
      JsonNode expectedPayload = objectMapper.valueToTree(dto);
      JsonNode actualPayload = objectMapper.valueToTree(captor.getValue().data());
      assertThat(actualPayload).isEqualTo(expectedPayload);
    }
  }
}