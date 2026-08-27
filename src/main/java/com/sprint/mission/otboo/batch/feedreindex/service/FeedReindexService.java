package com.sprint.mission.otboo.batch.feedreindex.service;

import com.sprint.mission.otboo.batch.feedreindex.config.FeedReindexProperties;
import com.sprint.mission.otboo.batch.feedreindex.exception.FeedReindexJobFailedException;
import com.sprint.mission.otboo.domain.social.feed.document.FeedDocument;
import java.time.Instant;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.core.BatchStatus;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.InvalidJobParametersException;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.launch.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.launch.JobOperator;
import org.springframework.batch.core.launch.JobRestartException;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedReindexService {

  private final JobOperator jobOperator;
  private final FeedReindexProperties feedReindexProperties;

  @Qualifier("feedReindexJob")
  private final Job feedReindexJob;

  @Qualifier("feedIncrementalReindexJob")
  private final Job feedIncrementalReindexJob;

  public void executeReindexAll() {
    log.info("피드 전체 재색인 배치 시작");
    run(feedReindexJob, baseParameters().toJobParameters());
    log.info("피드 전체 재색인 배치 완료");
  }

  public void executeIncrementalReindex() {
    Instant since = Instant.now().minus(feedReindexProperties.incrementalLookback());
    log.info("피드 증분 재색인 배치 시작: since={}", since);
    run(feedIncrementalReindexJob,
        baseParameters().addLong("since", since.toEpochMilli()).toJobParameters());
    log.info("피드 증분 재색인 배치 완료");
  }

  private JobParametersBuilder baseParameters() {
    return new JobParametersBuilder()
        .addLong("time", Instant.now().toEpochMilli())
        .addString("targetIndex", FeedDocument.INDEX_NAME);
  }

  private void run(Job job, JobParameters params) {
    try {
      JobExecution execution = jobOperator.start(job, params);
      if (execution.getStatus() != BatchStatus.COMPLETED) {
        throw FeedReindexJobFailedException.wrap(
            new IllegalStateException("Job 상태=" + execution.getStatus()));
      }
    } catch (JobExecutionAlreadyRunningException | JobRestartException
             | JobInstanceAlreadyCompleteException | InvalidJobParametersException e) {
      throw FeedReindexJobFailedException.wrap(e);
    }
  }
}
