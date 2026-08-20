package com.sprint.mission.otboo.domain.weathernotification.sse.listener;

import com.sprint.mission.otboo.domain.weathernotification.sse.dto.SseMessage;
import com.sprint.mission.otboo.domain.weathernotification.sse.service.SseService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@RequiredArgsConstructor
@Component
public class SseRedisMessageListener implements MessageListener {

  private final ObjectMapper objectMapper;
  private final SseService sseService;

  @Override
  public void onMessage(Message message, byte[] pattern) {
    String channel = new String(message.getChannel());
    try {
      SseMessage sseMessage = objectMapper.readValue(message.getBody(), SseMessage.class);
      sseService.deliverLocally(sseMessage);
    } catch (Exception e) {
      log.error("SSE Pub/Sub 메시지 처리 실패: channel={}", channel, e);
    }
  }
}