package com.sprint.mission.otboo.global.temppassword.registry;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.global.temppassword.generator.impl.RandomTempPasswordGenerator;
import com.sprint.mission.otboo.global.temppassword.properties.TempPasswordGeneratorType;
import com.sprint.mission.otboo.global.temppassword.properties.TempPasswordProperties;
import com.sprint.mission.otboo.global.temppassword.properties.TempPasswordRegistryType;
import com.sprint.mission.otboo.global.temppassword.registry.impl.TempPasswordRedisRegistry;
import com.sprint.mission.otboo.global.testcontainers.RedisTestContainerSupport;
import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

// 임시 비밀번호가 실제로 저장/암호화/만료되는지는 실제 Redis 없이는 검증할 수 없어 Testcontainers를 쓴다.
@DisplayName("TempPasswordRedisRegistry")
class TempPasswordRedisRegistryTest implements RedisTestContainerSupport {

  static LettuceConnectionFactory connectionFactory;
  static StringRedisTemplate redisTemplate;

  private TempPasswordRedisRegistry registry;

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
    redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    registry = new TempPasswordRedisRegistry(redisTemplate,
        new TempPasswordProperties(Duration.ofMinutes(3), TempPasswordGeneratorType.RANDOM,
            TempPasswordRegistryType.REDIS),
        new RandomTempPasswordGenerator(), new BCryptPasswordEncoder());
  }

  @Nested
  @DisplayName("저장 및 검증 - save, matches")
  class SaveAndMatches {

    @Test
    @DisplayName("저장한 원문 임시 비밀번호로 검증하면 일치한다")
    void 저장한_원문_임시_비밀번호로_검증하면_일치한다() {
      // given
      UUID userId = UUID.randomUUID();

      // when
      registry.save(userId, "temp-password1");

      // then
      assertThat(registry.matches(userId, "temp-password1")).isTrue();
    }

    @Test
    @DisplayName("저장된 값과 다른 비밀번호로 검증하면 일치하지 않는다")
    void 저장된_값과_다른_비밀번호로_검증하면_일치하지_않는다() {
      // given
      UUID userId = UUID.randomUUID();
      registry.save(userId, "temp-password1");

      // when & then
      assertThat(registry.matches(userId, "wrong-password")).isFalse();
    }

    @Test
    @DisplayName("저장된 적 없는 사용자는 검증하면 일치하지 않는다")
    void 저장된_적_없는_사용자는_검증하면_일치하지_않는다() {
      // when & then
      assertThat(registry.matches(UUID.randomUUID(), "temp-password1")).isFalse();
    }

    @Test
    @DisplayName("Redis에는 평문이 아니라 암호화된 값이 저장된다")
    void Redis에는_평문이_아니라_암호화된_값이_저장된다() {
      // given
      UUID userId = UUID.randomUUID();

      // when
      registry.save(userId, "temp-password1");

      // then
      String raw = redisTemplate.opsForValue().get("auth:temp-password:" + userId);
      assertThat(raw).isNotEqualTo("temp-password1");
    }

    @Test
    @DisplayName("저장 시 3분 이하의 TTL이 설정된다")
    void 저장_시_3분_이하의_TTL이_설정된다() {
      // given
      UUID userId = UUID.randomUUID();

      // when
      registry.save(userId, "temp-password1");

      // then
      Long ttl = redisTemplate.getExpire("auth:temp-password:" + userId, TimeUnit.SECONDS);
      assertThat(ttl).isNotNull();
      assertThat(ttl).isPositive();
      assertThat(ttl).isLessThanOrEqualTo(Duration.ofMinutes(3).toSeconds());
    }

    @Test
    @DisplayName("설정된 만료 시간을 분 단위로 반환한다")
    void 설정된_만료_시간을_분_단위로_반환한다() {
      // when & then
      assertThat(registry.getExpirationMinutes()).isEqualTo(3);
    }
  }

  @Nested
  @DisplayName("발급 - issue")
  class Issue {

    @Test
    @DisplayName("생성한 값을 저장하고 그대로 반환한다")
    void 생성한_값을_저장하고_그대로_반환한다() {
      // given
      UUID userId = UUID.randomUUID();

      // when
      String issued = registry.issue(userId);

      // then
      assertThat(issued).hasSize(12);
      assertThat(registry.matches(userId, issued)).isTrue();
    }
  }

  @Nested
  @DisplayName("폐기 - revoke")
  class Revoke {

    @Test
    @DisplayName("폐기하면 이후 검증은 항상 실패한다")
    void 폐기하면_이후_검증은_항상_실패한다() {
      // given
      UUID userId = UUID.randomUUID();
      registry.save(userId, "temp-password1");

      // when
      registry.revoke(userId);

      // then
      assertThat(registry.matches(userId, "temp-password1")).isFalse();
    }

    @Test
    @DisplayName("저장된 적 없는 사용자를 폐기해도 예외가 발생하지 않는다")
    void 저장된_적_없는_사용자를_폐기해도_예외가_발생하지_않는다() {
      // when & then
      registry.revoke(UUID.randomUUID());
    }
  }
}
