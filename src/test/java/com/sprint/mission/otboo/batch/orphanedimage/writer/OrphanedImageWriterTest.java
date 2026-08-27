package com.sprint.mission.otboo.batch.orphanedimage.writer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sprint.mission.otboo.batch.orphanedimage.metrics.OrphanedImageCleanupMetrics;
import com.sprint.mission.otboo.global.file.properties.FileImplType;
import com.sprint.mission.otboo.global.file.properties.FileProperties;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.infrastructure.item.Chunk;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectsRequest;
import software.amazon.awssdk.services.s3.model.DeleteObjectsResponse;
import software.amazon.awssdk.services.s3.model.DeletedObject;
import software.amazon.awssdk.services.s3.model.ObjectIdentifier;
import software.amazon.awssdk.services.s3.model.S3Error;

@ExtendWith(MockitoExtension.class)
class OrphanedImageWriterTest {

  @Mock
  private S3Client s3Client;
  @Mock
  private OrphanedImageCleanupMetrics metrics;
  private OrphanedImageWriter writer;

  @BeforeEach
  void setUp() {
    FileProperties fileProperties = new FileProperties(FileImplType.LOCAL,
        "http://localhost:8080/uploads", 5242880, Set.of("png"), null,
        new FileProperties.S3("otboo-uploads", "ap-northeast-2"));
    writer = new OrphanedImageWriter(s3Client, metrics, fileProperties);
  }

  @Nested
  @DisplayName("삭제")
  class Write {

    @Test
    @DisplayName("chunk의_키들을_한_번의_DeleteObjects로_삭제하고_실제_삭제된_건수를_계측한다")
    void chunk의_키들을_한_번의_DeleteObjects로_삭제하고_실제_삭제된_건수를_계측한다() {
      // given
      given(s3Client.deleteObjects(any(DeleteObjectsRequest.class))).willReturn(
          DeleteObjectsResponse.builder()
              .deleted(
                  DeletedObject.builder().key("profile/a.png").build(),
                  DeletedObject.builder().key("profile/b.png").build())
              .build());

      // when
      writer.write(Chunk.of("profile/a.png", "profile/b.png"));

      // then
      ArgumentCaptor<DeleteObjectsRequest> captor = ArgumentCaptor.forClass(DeleteObjectsRequest.class);
      verify(s3Client).deleteObjects(captor.capture());
      assertThat(captor.getValue().delete().objects())
          .extracting(ObjectIdentifier::key)
          .containsExactlyInAnyOrder("profile/a.png", "profile/b.png");
      verify(metrics).countDeleted(2);
    }

    @Test
    @DisplayName("일부_키만_삭제에_실패하면_실제_삭제된_건수만_계측하고_실패_키를_로그로_남긴다")
    void 일부_키만_삭제에_실패하면_실제_삭제된_건수만_계측하고_실패_키를_로그로_남긴다() {
      // given — 2개 중 1개만 삭제 성공, 1개는 응답의 errors()에 담겨 온다(예외 아님)
      given(s3Client.deleteObjects(any(DeleteObjectsRequest.class))).willReturn(
          DeleteObjectsResponse.builder()
              .deleted(DeletedObject.builder().key("profile/a.png").build())
              .errors(S3Error.builder()
                  .key("profile/b.png").code("AccessDenied").message("denied").build())
              .build());

      // when
      writer.write(Chunk.of("profile/a.png", "profile/b.png"));

      // then — 실제로 지워진 1건만 계측되고, Job 자체는 실패시키지 않는다
      verify(metrics).countDeleted(1);
    }

    @Test
    @DisplayName("빈_chunk는_S3를_호출하지_않는다")
    void 빈_chunk는_S3를_호출하지_않는다() {
      // when
      writer.write(Chunk.of());

      // then
      verify(s3Client, never()).deleteObjects(any(DeleteObjectsRequest.class));
    }
  }
}
