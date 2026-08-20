package com.sprint.mission.otboo.domain.weathernotification.sse.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sprint.mission.otboo.domain.weathernotification.sse.dto.SseMessage;
import com.sprint.mission.otboo.domain.weathernotification.sse.properties.SseReplayBufferProperties;
import com.sprint.mission.otboo.global.testcontainers.RedisTestContainerSupport;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

class SseMessageRepositoryTest implements RedisTestContainerSupport {

  private static final Instant NOW = Instant.now();

  static LettuceConnectionFactory connectionFactory;
  static StringRedisTemplate redisTemplate;

  private SseMessageRepository sseMessageRepository;

  @BeforeAll
  static void setUpRedis() {
    connectionFactory = new LettuceConnectionFactory(
        REDIS_CONTAINER.getHost(), REDIS_CONTAINER.getMappedPort(6379));
    connectionFactory.afterPropertiesSet();
    redisTemplate = new StringRedisTemplate(connectionFactory);
    redisTemplate.afterPropertiesSet();
  }

  @AfterAll
  static void tearDownRedis() {
    connectionFactory.destroy();
  }

  @BeforeEach
  void setUp() {
    sseMessageRepository = new SseMessageRepository(redisTemplate,
        new ObjectMapper().findAndRegisterModules(), Clock.fixed(NOW, ZoneOffset.UTC),
        new SseReplayBufferProperties(10));
  }

  @Nested
  @DisplayName("메시지 저장")
  class Save {

    @Test
    @DisplayName("저장하면 메시지의 id를 반환한다")
    void 저장하면_메시지의_id를_반환한다() {
      // given
      SseMessage message = new SseMessage(Set.of(UUID.randomUUID()), "notifications", "payload");

      // when & then
      assertThat(sseMessageRepository.save(message)).isEqualTo(message.id());
    }

    @Test
    @DisplayName("저장한 메시지는 Redis에 JSON으로 기록된다")
    void 저장한_메시지는_Redis에_JSON으로_기록된다() {
      // given
      SseMessage message = new SseMessage(Set.of(UUID.randomUUID()), "notifications", "payload");

      // when
      sseMessageRepository.save(message);

      // then
      assertThat(redisTemplate.opsForValue().get("sse:message:" + message.id())).isNotNull();
    }

    @Test
    @DisplayName("저장한 메시지의 id가 인덱스에 생성 시각 score로 등록된다")
    void 저장한_메시지의_id가_인덱스에_등록된다() {
      // given
      SseMessage message = new SseMessage(Set.of(UUID.randomUUID()), "notifications", "payload");

      // when
      sseMessageRepository.save(message);

      // then
      assertThat(redisTemplate.opsForZSet().score("sse:message-index", message.id().toString()))
          .isEqualTo((double) message.createdAt().toEpochMilli());
    }
  }
}