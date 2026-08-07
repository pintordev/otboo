package com.sprint.mission.otboo.batch.weatherfetch.listener;

import com.sprint.mission.otboo.external.kma.KmaBaseTimeCalculator;
import com.sprint.mission.otboo.external.kma.KmaBaseTimeCalculator.BaseTime;
import java.time.Clock;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.listener.JobExecutionListener;
import org.springframework.stereotype.Component;

@Slf4j
@RequiredArgsConstructor
@Component
public class WeatherFetchJobListener implements JobExecutionListener {

  private final Clock clock;

  @Override
  public void beforeJob(JobExecution jobExecution) {
    // baseTime을 Job 시작 시 한 번만 계산해 JobExecutionContext에 저장한다 - Reader/Processor가
    // 각자 Clock으로 다시 계산하면 Step 경계(특히 weatherFetchStep→weatherFetchRetryStep)를
    // 넘어갈 때 KMA 발표 시각이 바뀌어 서로 다른 baseTime을 쓸 수 있다
    BaseTime baseTime = KmaBaseTimeCalculator.calculate(clock.instant());
    jobExecution.getExecutionContext().putString("baseDate", baseTime.baseDate());
    jobExecution.getExecutionContext().putString("baseTime", baseTime.baseTime());

    log.info("WeatherFetch Job 시작 | jobId={}, params={}, baseDate={}, baseTime={}",
        jobExecution.getId(), jobExecution.getJobParameters(), baseTime.baseDate(),
        baseTime.baseTime());
  }

  @Override
  public void afterJob(JobExecution jobExecution) {
    // COMPLETED 분기는 향후 [FEAT] 날씨 급변 알림 트리거 이슈가 훅을 얹을 지점
    if (jobExecution.getStatus() == BatchStatus.COMPLETED) {
      log.info("WeatherFetch Job 성공 | jobId={}", jobExecution.getId());
    } else if (jobExecution.getStatus() == BatchStatus.FAILED) {
      log.error("WeatherFetch Job 실패 | jobId={}, exitStatus={}", jobExecution.getId(),
          jobExecution.getExitStatus());
    }

    if (jobExecution.getStartTime() != null && jobExecution.getEndTime() != null) {
      Duration duration = Duration.between(jobExecution.getStartTime(), jobExecution.getEndTime());
      log.info("WeatherFetch Job duration={}", duration);
    }

    if (!jobExecution.getAllFailureExceptions().isEmpty()) {
      jobExecution.getAllFailureExceptions()
          .forEach(e -> log.error("WeatherFetch Job 실패 원인", e));
    }
  }
}