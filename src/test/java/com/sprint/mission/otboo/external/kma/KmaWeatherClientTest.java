package com.sprint.mission.otboo.external.kma;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.external.kma.dto.KmaWeatherResponse;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

@Tag("external")
@SpringBootTest
class KmaWeatherClientTest {

  @Autowired
  private KmaWeatherClient kmaWeatherClient;

  @Value("${weather.kma.service-key}")
  private String serviceKey;

  @Nested
  @DisplayName("GetVillageForecast")
  class GetVillageForecast {

    @Test
    @DisplayName("실제_기상청_API를_호출하면_정상_응답을_받는다")
    void 실제_기상청_API를_호출하면_정상_응답을_받는다() {
      // given - 어제 23시 발표는 언제 실행하든 항상 이미 공개된 자료임
      String baseDate = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1)
          .format(DateTimeFormatter.ofPattern("yyyyMMdd"));

      // when
      KmaWeatherResponse response = kmaWeatherClient.getVillageForecast(
          serviceKey, 100, 1, "JSON", baseDate, "2300", 60, 127);

      // then
      assertThat(response.response().header().resultCode()).isEqualTo("00");
      assertThat(response.response().body().items().item()).isNotEmpty();
    }
  }
}