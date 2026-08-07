package com.sprint.mission.otboo.batch.weatherretention.scheduler;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import com.sprint.mission.otboo.batch.weatherretention.service.WeatherRetentionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WeatherRetentionSchedulerTest {

  @InjectMocks
  private WeatherRetentionScheduler scheduler;

  @Mock
  private WeatherRetentionService weatherRetentionService;

  @Nested
  @DisplayName("CleanUp")
  class CleanUp {

    @Test
    @DisplayName("WeatherRetentionService_execute만_호출하고_다른_로직은_없다")
    void WeatherRetentionService_execute만_호출하고_다른_로직은_없다() {
      // when
      scheduler.cleanUp();

      // then
      verify(weatherRetentionService).execute();
      verifyNoMoreInteractions(weatherRetentionService);
    }
  }
}