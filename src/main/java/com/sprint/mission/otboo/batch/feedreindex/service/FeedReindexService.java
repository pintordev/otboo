package com.sprint.mission.otboo.batch.feedreindex.service;

import com.sprint.mission.otboo.batch.feedreindex.config.FeedReindexProperties;
import com.sprint.mission.otboo.batch.feedreindex.dto.FeedReindexResult;
import com.sprint.mission.otboo.batch.feedreindex.exception.FeedReindexAlreadyRunningException;
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
import org.springframework.batch.core.step.StepExecution;
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

  public FeedReindexResult executeReindexAll() {
    long startedAt = System.currentTimeMillis();
    log.info("피드 전체 재색인 배치 시작");
    JobExecution execution = run(feedReindexJob, baseParameters().toJobParameters());
    log.info("피드 전체 재색인 배치 완료");
    return toResult(execution, System.currentTimeMillis() - startedAt);
  }

  public void executeIncrementalReindex() {
    Instant since = Instant.now().minus(feedReindexProperties.incrementalLookback());
    log.info("피드 증분 재색인 배치 시작: since={}", since);
    run(feedIncrementalReindexJob,
        baseParameters().addLong("since", since.toEpochMilli()).toJobParameters());
    log.info("피드 증분 재색인 배치 완료");
  }

  private FeedReindexResult toResult(JobExecution execution, long elapsedMillis) {
    long readCount = execution.getStepExecutions().stream()
        .mapToLong(StepExecution::getReadCount)
        .sum();
    long writeCount = execution.getStepExecutions().stream()
        .mapToLong(StepExecution::getWriteCount)
        .sum();
    return new FeedReindexResult(readCount, writeCount, elapsedMillis);
  }

  private JobParametersBuilder baseParameters() {
    return new JobParametersBuilder()
        .addLong("time", Instant.now().toEpochMilli())
        .addString("targetIndex", FeedDocument.INDEX_NAME);
  }

  private JobExecution run(Job job, JobParameters params) {
    try {
      JobExecution execution = jobOperator.start(job, params);
      if (execution.getStatus() != BatchStatus.COMPLETED) {
        throw FeedReindexJobFailedException.wrap(
            new IllegalStateException("Job 상태=" + execution.getStatus()));
      }
      return execution;
    } catch (JobExecutionAlreadyRunningException e) {
      throw FeedReindexAlreadyRunningException.wrap(e);
    } catch (JobRestartException | JobInstanceAlreadyCompleteException
             | InvalidJobParametersException e) {
      throw FeedReindexJobFailedException.wrap(e);
    }
  }
}
