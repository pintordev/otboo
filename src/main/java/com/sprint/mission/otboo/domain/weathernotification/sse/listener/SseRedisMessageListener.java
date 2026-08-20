package com.sprint.mission.otboo.domain.weathernotification.sse.listener;

import com.sprint.mission.otboo.domain.weathernotification.sse.dto.SseMessage;
import com.sprint.mission.otboo.domain.weathernotification.sse.service.SseService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@RequiredArgsConstructor
@Component
public class SseRedisMessageListener implements MessageListener {

  private final ObjectMapper objectMapper;
  private final SseService sseService;

  @Override
  public void onMessage(Message message, byte[] pattern) {
    SseMessage sseMessage = objectMapper.readValue(message.getBody(), SseMessage.class);
    sseService.deliverLocally(sseMessage);
  }
}