package com.sprint.mission.otboo.domain.weathernotification.weather.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.ConstructorPropertiesArbitraryIntrospector;
import com.sprint.mission.otboo.external.kakao.KakaoFeignProperties;
import com.sprint.mission.otboo.external.kma.KmaFeignProperties;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SingleFlightLeaseTimeoutValidatorTest {

  private static final FixtureMonkey FIXTURE_MONKEY = FixtureMonkey.builder()
      .objectIntrospector(ConstructorPropertiesArbitraryIntrospector.INSTANCE)
      .build();

  @Nested
  @DisplayName("검증")
  class Validate {

    @Test
    @DisplayName("kma_kakao_타임아웃_합이_lock_ttl보다_짧으면_예외를_던지지_않는다")
    void kma_kakao_타임아웃_합이_lock_ttl보다_짧으면_예외를_던지지_않는다() {
      // given
      SingleFlightProperties singleFlightProperties = FIXTURE_MONKEY
          .giveMeBuilder(SingleFlightProperties.class)
          .set("lockTtl", Duration.ofSeconds(10))
          .sample();
      KmaFeignProperties kmaFeignProperties = FIXTURE_MONKEY
          .giveMeBuilder(KmaFeignProperties.class)
          .set("connect", Duration.ofSeconds(2))
          .set("read", Duration.ofSeconds(5))
          .sample();
      KakaoFeignProperties kakaoFeignProperties = FIXTURE_MONKEY
          .giveMeBuilder(KakaoFeignProperties.class)
          .set("connect", Duration.ofSeconds(2))
          .set("read", Duration.ofSeconds(5))
          .sample();
      SingleFlightLeaseTimeoutValidator validator = new SingleFlightLeaseTimeoutValidator(
          singleFlightProperties, kmaFeignProperties, kakaoFeignProperties);

      // when & then
      assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("kma_타임아웃_합이_lock_ttl_이상이면_예외를_던진다")
    void kma_타임아웃_합이_lock_ttl_이상이면_예외를_던진다() {
      // given
      SingleFlightProperties singleFlightProperties = FIXTURE_MONKEY
          .giveMeBuilder(SingleFlightProperties.class)
          .set("lockTtl", Duration.ofSeconds(10))
          .sample();
      KmaFeignProperties kmaFeignProperties = FIXTURE_MONKEY
          .giveMeBuilder(KmaFeignProperties.class)
          .set("connect", Duration.ofSeconds(5))
          .set("read", Duration.ofSeconds(5))
          .sample();
      KakaoFeignProperties kakaoFeignProperties = FIXTURE_MONKEY
          .giveMeBuilder(KakaoFeignProperties.class)
          .set("connect", Duration.ofSeconds(2))
          .set("read", Duration.ofSeconds(5))
          .sample();
      SingleFlightLeaseTimeoutValidator validator = new SingleFlightLeaseTimeoutValidator(
          singleFlightProperties, kmaFeignProperties, kakaoFeignProperties);

      // when & then
      assertThatThrownBy(validator::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("kakao_타임아웃_합이_lock_ttl_이상이면_예외를_던진다")
    void kakao_타임아웃_합이_lock_ttl_이상이면_예외를_던진다() {
      // given
      SingleFlightProperties singleFlightProperties = FIXTURE_MONKEY
          .giveMeBuilder(SingleFlightProperties.class)
          .set("lockTtl", Duration.ofSeconds(10))
          .sample();
      KmaFeignProperties kmaFeignProperties = FIXTURE_MONKEY
          .giveMeBuilder(KmaFeignProperties.class)
          .set("connect", Duration.ofSeconds(2))
          .set("read", Duration.ofSeconds(5))
          .sample();
      KakaoFeignProperties kakaoFeignProperties = FIXTURE_MONKEY
          .giveMeBuilder(KakaoFeignProperties.class)
          .set("connect", Duration.ofSeconds(8))
          .set("read", Duration.ofSeconds(2))
          .sample();
      SingleFlightLeaseTimeoutValidator validator = new SingleFlightLeaseTimeoutValidator(
          singleFlightProperties, kmaFeignProperties, kakaoFeignProperties);

      // when & then
      assertThatThrownBy(validator::validate).isInstanceOf(IllegalStateException.class);
    }
  }
}