package com.sprint.mission.otboo.domain.weathernotification.weather.entity;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.global.testcontainers.IntegrationTestSupport;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest
@ActiveProfiles("test")
class WeatherBaselineMigrationTest extends IntegrationTestSupport {

  @Autowired
  private JdbcTemplate jdbcTemplate;

  @Test
  @DisplayName("baseline_컬럼은_NOT_NULL_제약을_가진다")
  void baseline_컬럼은_NOT_NULL_제약을_가진다() {
    // when
    List<String> nullableColumns = jdbcTemplate.queryForList(
        "SELECT column_name FROM information_schema.columns "
            + "WHERE table_name = 'weathers' AND column_name LIKE 'baseline_%' "
            + "AND is_nullable = 'YES'", String.class);

    // then
    assertThat(nullableColumns).isEmpty();
  }
}