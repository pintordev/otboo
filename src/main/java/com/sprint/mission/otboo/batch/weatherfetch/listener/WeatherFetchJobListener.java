package com.sprint.mission.otboo.batch.weatherfetch.listener;

import com.sprint.mission.otboo.batch.weatherfetch.service.WeatherSuddenChangeNotifier;
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
  private final WeatherSuddenChangeNotifier weatherSuddenChangeNotifier;

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

    // Job 전체가 FAILED로 끝났어도, findGridsUpdatedAt()이 실제로 새 데이터가 들어온 격자만
    // 골라내므로 그 전에 성공 처리된 격자의 감지·알림은 그대로 유효하다 - COMPLETED/FAILED
    // 분기와 무관하게 항상 호출한다. 이 예외가 Job의 최종 상태를 되돌리면 안 되므로 흡수한다.
    try {
      String baseDate = jobExecution.getExecutionContext().getString("baseDate");
      String baseTimeValue = jobExecution.getExecutionContext().getString("baseTime");
      weatherSuddenChangeNotifier.detectAndNotify(new BaseTime(baseDate, baseTimeValue));
    } catch (Exception e) {
      log.error("날씨 급변 감지 실패", e);
    }
  }
}