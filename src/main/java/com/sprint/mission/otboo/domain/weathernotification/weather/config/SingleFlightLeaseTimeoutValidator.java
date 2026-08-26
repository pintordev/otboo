package com.sprint.mission.otboo.domain.weathernotification.weather.config;

import com.sprint.mission.otboo.external.kakao.KakaoFeignProperties;
import com.sprint.mission.otboo.external.kma.KmaFeignProperties;
import jakarta.annotation.PostConstruct;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

// SingleFlightRegistry는 락 lease를 갱신하지 않으므로, work(외부 API 호출 + 저장)가 항상
// lock TTL 안에 끝난다는 전제가 성립해야 한다. 그 전제를 이루는 설정(single-flight.lock-ttl,
// kma/kakao.timeout.*)이 서로 다른 파일에 있어 한쪽만 바뀌면 조용히 어긋날 수 있으므로,
// 기동 시점에 실제 설정값으로 이 관계를 검증해 어긋나면 앱을 띄우지 않는다.
@Component
@RequiredArgsConstructor
public class SingleFlightLeaseTimeoutValidator {

  private final SingleFlightProperties singleFlightProperties;
  private final KmaFeignProperties kmaFeignProperties;
  private final KakaoFeignProperties kakaoFeignProperties;

  @PostConstruct
  void validate() {
    validateAgainstLockTtl("kma", kmaFeignProperties.connect(), kmaFeignProperties.read());
    validateAgainstLockTtl("kakao", kakaoFeignProperties.connect(), kakaoFeignProperties.read());
  }

  private void validateAgainstLockTtl(String clientName, Duration connectTimeout,
      Duration readTimeout) {
    Duration worstCase = connectTimeout.plus(readTimeout);
    Duration lockTtl = singleFlightProperties.lockTtl();
    if (worstCase.compareTo(lockTtl) >= 0) {
      throw new IllegalStateException(
          "%s Feign 타임아웃 합(%s)이 weather.single-flight.lock-ttl(%s)보다 짧지 않습니다 - "
              .formatted(clientName, worstCase, lockTtl)
              + "락이 만료되기 전에 work가 끝난다는 보장이 깨집니다. 설정을 다시 맞춰주세요.");
    }
  }
}