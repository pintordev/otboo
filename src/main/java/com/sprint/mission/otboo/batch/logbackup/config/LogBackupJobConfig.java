package com.sprint.mission.otboo.batch.logbackup.config;

import com.sprint.mission.otboo.batch.logbackup.dto.LogContent;
import com.sprint.mission.otboo.batch.logbackup.dto.UploadPayload;
import com.sprint.mission.otboo.batch.logbackup.exception.LogBackupFailedException;
import com.sprint.mission.otboo.batch.logbackup.listener.LogBackupJobListener;
import com.sprint.mission.otboo.batch.logbackup.listener.LogBackupStepListener;
import com.sprint.mission.otboo.batch.logbackup.processor.LogBackupProcessor;
import com.sprint.mission.otboo.batch.logbackup.reader.LogBackupReader;
import com.sprint.mission.otboo.batch.logbackup.writer.LogBackupWriter;
import com.sprint.mission.otboo.global.batch.SkipLoggingListener;
import lombok.RequiredArgsConstructor;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.Step;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
@RequiredArgsConstructor
@EnableConfigurationProperties(LogBackupProperties.class)
public class LogBackupJobConfig {

  private final JobRepository jobRepository;
  private final PlatformTransactionManager transactionManager;

  private final LogBackupJobListener logBackupJobListener;
  private final LogBackupStepListener logBackupStepListener;
  private final SkipLoggingListener skipLoggingListener;
  private final LogBackupReader logBackupReader;
  private final LogBackupProcessor logBackupProcessor;
  private final LogBackupWriter logBackupWriter;
  private final LogBackupProperties logBackupProperties;

  @Bean(name = "logBackupJob")
  public Job logBackupJob() {
    return new JobBuilder("logBackupJob", jobRepository)
        .listener(logBackupJobListener)
        .start(logBackupStep())
        .build();
  }

  @Bean
  public Step logBackupStep() {
    return new StepBuilder("logBackupStep", jobRepository)
        .<LogContent, UploadPayload>chunk(logBackupProperties.chunkSize())
        .transactionManager(transactionManager)
        .reader(logBackupReader)
        .processor(logBackupProcessor)
        .writer(logBackupWriter)
        .faultTolerant()
        .retryLimit(logBackupProperties.retryLimit())
        .retry(TransientDataAccessException.class)
        .retry(LogBackupFailedException.class)
        // LogBackupReadFailedException(Reader 실패)은 의도적으로 제외한다 - Reader는 실패 시
        // nextToken/currentTargetDone 등 페이지네이션 상태를 전진시키지 않으므로, skip 대상에 넣으면
        // Spring Batch가 다음 read()에서 같은 요청을 그대로 반복해 skipLimit만 소모하고 진행이 없다.
        // 등록하지 않은 예외는 곧바로 Step을 실패시키므로 CloudWatch 조회 실패는 즉시 Job 실패로 처리한다.
        .skip(LogBackupFailedException.class)
        .skipLimit(logBackupProperties.skipLimit())
        .skipListener(skipLoggingListener)
        .listener(logBackupStepListener)
        .build();
  }
}
