package com.sprint.mission.otboo.batch.orphanedimage.writer;

import com.sprint.mission.otboo.batch.orphanedimage.metrics.OrphanedImageCleanupMetrics;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.batch.infrastructure.item.Chunk;
import org.springframework.batch.infrastructure.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.Delete;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;

@Slf4j
@Component
public class OrphanedImageWriter implements ItemWriter<String> {

  private final S3Client s3Client;
  private final OrphanedImageCleanupMetrics metrics;
  private final String bucket;

  public OrphanedImageWriter(S3Client s3Client, OrphanedImageCleanupMetrics metrics,
      @Value("${otboo.file.s3.bucket}") String bucket) {
    this.s3Client = s3Client;
    this.metrics = metrics;
    this.bucket = bucket;
  }

  @Override
  public void write(Chunk<? extends String> chunk) {
    if (chunk.isEmpty()) {
      return;
    }
    var objectIds = chunk.getItems().stream()
        .map(key -> ObjectIdentifier.builder().key(key).build())
        .toList();
    DeleteObjectsResponse response = s3Client.deleteObjects(DeleteObjectsRequest.builder()
        .bucket(bucket)
        .delete(Delete.builder().objects(objectIds).build())
        .build());

    metrics.countDeleted(response.deleted().size());
    log.info("유실 이미지 삭제 완료: size={}", response.deleted().size());

    if (!response.errors().isEmpty()) {
      response.errors().forEach(error -> log.error(
          "유실 이미지 삭제 실패: key={}, code={}, message={}",
          error.key(), error.code(), error.message()));
    }
  }
}
