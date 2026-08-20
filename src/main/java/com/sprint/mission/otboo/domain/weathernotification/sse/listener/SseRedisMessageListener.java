package com.sprint.mission.otboo.domain.weathernotification.sse.listener;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprint.mission.otboo.domain.weathernotification.sse.dto.SseMessage;
import com.sprint.mission.otboo.domain.weathernotification.sse.service.SseService;
import java.io.IOException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

@RequiredArgsConstructor
@Component
public class SseRedisMessageListener implements MessageListener {

  private final ObjectMapper objectMapper;
  private final SseService sseService;

  @Override
  public void onMessage(Message message, byte[] pattern) {
    sseService.deliverLocally(readJson(message.getBody()));
  }

  private SseMessage readJson(byte[] body) {
    try {
      return objectMapper.readValue(body, SseMessage.class);
    } catch (IOException e) {
      throw new IllegalStateException("SseMessage 역직렬화 실패", e);
    }
  }
}