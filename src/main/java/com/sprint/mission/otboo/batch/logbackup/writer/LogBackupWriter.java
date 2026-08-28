package com.sprint.mission.otboo.batch.logbackup.writer;

import com.sprint.mission.otboo.batch.logbackup.config.LogBackupProperties;
import com.sprint.mission.otboo.batch.logbackup.dto.UploadPayload;
import com.sprint.mission.otboo.batch.logbackup.exception.LogBackupFailedException;
import com.sprint.mission.otboo.batch.logbackup.metrics.LogBackupMetrics;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Slf4j
@Component
public class LogBackupWriter implements ItemWriter<UploadPayload> {

  private final S3Client s3Client;
  private final LogBackupMetrics metrics;
  private final String bucket;

  public LogBackupWriter(S3Client s3Client, LogBackupMetrics metrics,
      LogBackupProperties logBackupProperties) {
    this.s3Client = s3Client;
    this.metrics = metrics;
    this.bucket = logBackupProperties.s3Bucket();
  }

  @Override
  public void write(Chunk<? extends UploadPayload> chunk) {
    for (UploadPayload item : chunk) {
      if (exists(item.s3Key())) {
        log.info("이미 존재 → skip: {}", item.s3Key());
        metrics.countSkipped();
        continue;
      }
      doUpload(item);
    }
  }

  private boolean exists(String key) {
    try {
      s3Client.headObject(b -> b.bucket(bucket).key(key));
      return true;
    } catch (NoSuchKeyException e) {
      return false;
    } catch (Exception e) {
      metrics.countFailed();
      log.error("S3 존재 여부 확인 중 오류 발생: {}", key, e);
      throw LogBackupFailedException.withKey(key, e);
    }
  }

  private void doUpload(UploadPayload item) {
    try {
      s3Client.putObject(
          PutObjectRequest.builder().bucket(bucket).key(item.s3Key())
              .contentType("application/gzip")
              .contentLength((long) item.compressedData().length)
              .build(),
          RequestBody.fromBytes(item.compressedData()));
      metrics.countUploaded();
      metrics.recordBytes(item.compressedData().length);
      log.info("업로드 완료: {}", item.s3Key());
    } catch (Exception e) {
      metrics.countFailed();
      throw LogBackupFailedException.withKey(item.s3Key(), e);
    }
  }
}
