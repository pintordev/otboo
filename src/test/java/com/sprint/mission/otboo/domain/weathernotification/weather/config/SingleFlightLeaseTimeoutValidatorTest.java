package com.sprint.mission.otboo.domain.weathernotification.weather.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.mission.otboo.external.kakao.KakaoFeignProperties;
import com.sprint.mission.otboo.external.kma.KmaFeignProperties;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class SingleFlightLeaseTimeoutValidatorTest {

  @Nested
  @DisplayName("Validate")
  class Validate {

    @Test
    @DisplayName("kma_kakao_타임아웃_합이_lock_ttl보다_짧으면_예외를_던지지_않는다")
    void kma_kakao_타임아웃_합이_lock_ttl보다_짧으면_예외를_던지지_않는다() {
      // given
      SingleFlightProperties singleFlightProperties = new SingleFlightProperties(
          Duration.ofSeconds(10));
      KmaFeignProperties kmaFeignProperties = new KmaFeignProperties(
          Duration.ofSeconds(2), Duration.ofSeconds(5));
      KakaoFeignProperties kakaoFeignProperties = new KakaoFeignProperties(
          Duration.ofSeconds(2), Duration.ofSeconds(5));
      SingleFlightLeaseTimeoutValidator validator = new SingleFlightLeaseTimeoutValidator(
          singleFlightProperties, kmaFeignProperties, kakaoFeignProperties);

      // when & then
      assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("kma_타임아웃_합이_lock_ttl_이상이면_예외를_던진다")
    void kma_타임아웃_합이_lock_ttl_이상이면_예외를_던진다() {
      // given
      SingleFlightProperties singleFlightProperties = new SingleFlightProperties(
          Duration.ofSeconds(10));
      KmaFeignProperties kmaFeignProperties = new KmaFeignProperties(
          Duration.ofSeconds(5), Duration.ofSeconds(5));
      KakaoFeignProperties kakaoFeignProperties = new KakaoFeignProperties(
          Duration.ofSeconds(2), Duration.ofSeconds(5));
      SingleFlightLeaseTimeoutValidator validator = new SingleFlightLeaseTimeoutValidator(
          singleFlightProperties, kmaFeignProperties, kakaoFeignProperties);

      // when & then
      assertThatThrownBy(validator::validate).isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("kakao_타임아웃_합이_lock_ttl_이상이면_예외를_던진다")
    void kakao_타임아웃_합이_lock_ttl_이상이면_예외를_던진다() {
      // given
      SingleFlightProperties singleFlightProperties = new SingleFlightProperties(
          Duration.ofSeconds(10));
      KmaFeignProperties kmaFeignProperties = new KmaFeignProperties(
          Duration.ofSeconds(2), Duration.ofSeconds(5));
      KakaoFeignProperties kakaoFeignProperties = new KakaoFeignProperties(
          Duration.ofSeconds(8), Duration.ofSeconds(2));
      SingleFlightLeaseTimeoutValidator validator = new SingleFlightLeaseTimeoutValidator(
          singleFlightProperties, kmaFeignProperties, kakaoFeignProperties);

      // when & then
      assertThatThrownBy(validator::validate).isInstanceOf(IllegalStateException.class);
    }
  }
}