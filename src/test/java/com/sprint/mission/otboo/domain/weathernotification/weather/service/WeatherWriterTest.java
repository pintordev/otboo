package com.sprint.mission.otboo.domain.weathernotification.weather.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.ConstructorPropertiesArbitraryIntrospector;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.Weather;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.WeatherGrid;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.PrecipitationType;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.SkyStatus;
import com.sprint.mission.otboo.domain.weathernotification.weather.entity.enums.WindStrength;
import com.sprint.mission.otboo.domain.weathernotification.weather.repository.WeatherRepository;
import com.sprint.mission.otboo.external.kma.dto.WeatherForecastSlotDto;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class WeatherWriterTest {

  private static final FixtureMonkey FIXTURE_MONKEY = FixtureMonkey.builder()
      .objectIntrospector(ConstructorPropertiesArbitraryIntrospector.INSTANCE)
      .build();

  @Mock
  private WeatherRepository weatherRepository;

  @Mock
  private JdbcTemplate jdbcTemplate;

  private WeatherWriter weatherWriter;

  @BeforeEach
  void setUp() {
    weatherWriter = new WeatherWriter(weatherRepository, jdbcTemplate);
  }


  @Nested
  @DisplayName("BuildSlots")
  class BuildSlots {

    @Test
    @DisplayName("슬롯의_baseline은_현재_값으로_채워진다")
    void 슬롯의_baseline은_현재_값으로_채워진다() {
      // given
      WeatherGrid weatherGrid = WeatherGrid.create(60, 127);
      Instant forecastedAt = Instant.parse("2026-07-27T08:00:00Z");
      WeatherForecastSlotDto slot = FIXTURE_MONKEY.giveMeBuilder(WeatherForecastSlotDto.class)
          .set("date", LocalDate.of(2026, 7, 27))
          .set("slotAt", Instant.parse("2026-07-27T00:00:00Z"))
          .set("temperatureCurrent", 28.0)
          .set("precipitationType", PrecipitationType.RAIN)
          .set("precipitationProbability", 60.0)
          .set("precipitationAmount", 5.0)
          .sample();

      // when
      List<Weather> result = weatherWriter.buildSlots(weatherGrid, forecastedAt, List.of(slot),
          Map.of());

      // then
      assertThat(result).hasSize(1);
      Weather built = result.get(0);
      assertThat(built.getBaselineTemperatureCurrent()).isEqualTo(28.0);
      assertThat(built.getBaselinePrecipitationType()).isEqualTo(PrecipitationType.RAIN);
      assertThat(built.getBaselinePrecipitationProbability()).isEqualTo(60.0);
      assertThat(built.getBaselinePrecipitationAmount()).isEqualTo(5.0);
    }

    @Test
    @DisplayName("어제_같은_슬롯이_있으면_diff를_계산한다")
    void 어제_같은_슬롯이_있으면_diff를_계산한다() {
      // given
      WeatherGrid weatherGrid = WeatherGrid.create(60, 127);
      Instant forecastedAt = Instant.parse("2026-07-27T08:00:00Z");
      Instant slotAt = Instant.parse("2026-07-27T00:00:00Z");
      Weather yesterdaySameSlot = Weather.create(weatherGrid,
          Instant.parse("2026-07-26T08:00:00Z"), slotAt.minus(1, ChronoUnit.DAYS),
          SkyStatus.CLEAR, PrecipitationType.NONE, 0.0, 0.0, 60.0, null, 26.0, null, 24.0, 29.0,
          2.0, WindStrength.WEAK, 26.0, PrecipitationType.NONE, 0.0, 0.0);
      WeatherForecastSlotDto slot = FIXTURE_MONKEY.giveMeBuilder(WeatherForecastSlotDto.class)
          .set("date", LocalDate.of(2026, 7, 27))
          .set("slotAt", slotAt)
          .set("temperatureCurrent", 28.0)
          .set("humidityCurrent", 65.0)
          .sample();

      // when
      List<Weather> result = weatherWriter.buildSlots(weatherGrid, forecastedAt, List.of(slot),
          Map.of(slotAt.minus(1, ChronoUnit.DAYS), yesterdaySameSlot));

      // then
      assertThat(result).hasSize(1);
      assertThat(result.get(0).getTemperatureCompared()).isEqualTo(2.0); // 28.0 - 26.0
      assertThat(result.get(0).getHumidityCompared()).isEqualTo(5.0); // 65.0 - 60.0
    }
  }

  @Nested
  @DisplayName("SaveSlots")
  class SaveSlots {

    @Test
    @DisplayName("upsert_SQL은_baseline_컬럼을_DO_UPDATE_SET_목록에서_제외한다")
    void upsert_SQL은_baseline_컬럼을_DO_UPDATE_SET_목록에서_제외한다() {
      // given
      WeatherGrid weatherGrid = WeatherGrid.create(60, 127);
      Instant forecastedAt = Instant.parse("2026-07-27T08:00:00Z");
      WeatherForecastSlotDto slot = FIXTURE_MONKEY.giveMeBuilder(WeatherForecastSlotDto.class)
          .set("date", LocalDate.of(2026, 7, 27))
          .set("slotAt", Instant.parse("2026-07-27T00:00:00Z"))
          .sample();
      Instant slotAt = slot.slotAt();
      given(jdbcTemplate.batchUpdate(anyString(), any(BatchPreparedStatementSetter.class)))
          .willReturn(new int[]{1});
      given(weatherRepository.findAllByWeatherGridAndForecastAtInOrderByForecastAt(
          eq(weatherGrid), eq(List.of(slotAt))))
          .willReturn(List.of());

      // when
      weatherWriter.saveSlots(weatherGrid, forecastedAt, List.of(slot), Map.of());

      // then
      ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
      verify(jdbcTemplate).batchUpdate(sqlCaptor.capture(),
          any(BatchPreparedStatementSetter.class));
      assertThat(sqlCaptor.getValue())
          .contains("ON CONFLICT (weather_grid_id, forecast_at) DO UPDATE SET")
          .doesNotContain("baseline_temperature_current = EXCLUDED")
          .doesNotContain("baseline_precipitation_type = EXCLUDED")
          .doesNotContain("baseline_precipitation_probability = EXCLUDED")
          .doesNotContain("baseline_precipitation_amount = EXCLUDED");
    }

    @Test
    @DisplayName("baseline_파라미터를_PreparedStatement에_바인딩한다")
    void baseline_파라미터를_PreparedStatement에_바인딩한다() throws Exception {
      // given
      WeatherGrid weatherGrid = WeatherGrid.create(60, 127);
      Instant forecastedAt = Instant.parse("2026-07-27T08:00:00Z");
      WeatherForecastSlotDto slot = FIXTURE_MONKEY.giveMeBuilder(WeatherForecastSlotDto.class)
          .set("date", LocalDate.of(2026, 7, 27))
          .set("slotAt", Instant.parse("2026-07-27T00:00:00Z"))
          .set("skyStatus", SkyStatus.CLEAR)
          .set("temperatureCurrent", 28.0)
          .set("precipitationType", PrecipitationType.RAIN)
          .set("precipitationProbability", 60.0)
          .set("precipitationAmount", 5.0)
          .sample();
      Instant slotAt = slot.slotAt();
      given(jdbcTemplate.batchUpdate(anyString(), any(BatchPreparedStatementSetter.class)))
          .willReturn(new int[]{1});
      given(weatherRepository.findAllByWeatherGridAndForecastAtInOrderByForecastAt(
          eq(weatherGrid), eq(List.of(slotAt))))
          .willReturn(List.of());

      // when
      weatherWriter.saveSlots(weatherGrid, forecastedAt, List.of(slot), Map.of());

      // then
      ArgumentCaptor<BatchPreparedStatementSetter> setterCaptor = ArgumentCaptor.forClass(
          BatchPreparedStatementSetter.class);
      verify(jdbcTemplate).batchUpdate(anyString(), setterCaptor.capture());
      PreparedStatement preparedStatement = mock(PreparedStatement.class);
      setterCaptor.getValue().setValues(preparedStatement, 0);
      verify(preparedStatement).setObject(17, 28.0, Types.DOUBLE);
      verify(preparedStatement).setString(18, "RAIN");
      verify(preparedStatement).setObject(19, 60.0, Types.DOUBLE);
      verify(preparedStatement).setObject(20, 5.0, Types.DOUBLE);
    }
  }
}