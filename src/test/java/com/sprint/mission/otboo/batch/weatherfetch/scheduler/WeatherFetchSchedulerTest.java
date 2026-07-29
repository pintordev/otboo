package com.sprint.mission.otboo.batch.weatherfetch.scheduler;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.sprint.mission.otboo.batch.weatherfetch.service.WeatherFetchService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeatherFetchSchedulerTest {

  @InjectMocks
  private WeatherFetchScheduler scheduler;

  @Mock
  private WeatherFetchService weatherFetchService;

  @Nested
  @DisplayName("Fetch")
  class Fetch {

    @Test
    @DisplayName("WeatherFetchService_execute만_호출하고_다른_로직은_없다")
    void WeatherFetchService_execute만_호출하고_다른_로직은_없다() {
      // when
      scheduler.fetch();

      // then
      verify(weatherFetchService).execute();
      verifyNoMoreInteractions(weatherFetchService);
    }
  }
}