package com.sprint.mission.otboo.batch.logbackup.writer;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.ConstructorPropertiesArbitraryIntrospector;
import com.sprint.mission.otboo.batch.logbackup.config.LogBackupProperties;
import com.sprint.mission.otboo.batch.logbackup.config.LogBackupProperties.LogGroupTarget;
import com.sprint.mission.otboo.batch.logbackup.dto.UploadPayload;
import com.sprint.mission.otboo.batch.logbackup.exception.LogBackupFailedException;
import com.sprint.mission.otboo.batch.logbackup.metrics.LogBackupMetrics;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.batch.infrastructure.item.Chunk;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.NoSuchKeyException;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@ExtendWith(MockitoExtension.class)
class LogBackupWriterTest {

  private static final FixtureMonkey FIXTURE_MONKEY = FixtureMonkey.builder()
      .objectIntrospector(ConstructorPropertiesArbitraryIntrospector.INSTANCE)
      .build();

  @Mock
  private S3Client s3Client;
  @Mock
  private LogBackupMetrics metrics;
  private LogBackupWriter writer;

  @BeforeEach
  void setUp() {
    LogBackupProperties properties = new LogBackupProperties(
        List.of(new LogGroupTarget("/ecs/otboo", "app")), "otboo-backup", 10, 5, 3, 3);
    writer = new LogBackupWriter(s3Client, metrics, properties);
  }

  private UploadPayload uploadPayload() {
    return FIXTURE_MONKEY.giveMeBuilder(UploadPayload.class)
        .set("s3Key", "logs/app/2026/08/20/app-20260820-001.log.gz")
        .set("compressedData", "gzipped".getBytes(StandardCharsets.UTF_8))
        .sample();
  }

  @Nested
  @DisplayName("업로드")
  class Write {

    @Test
    @DisplayName("존재하지_않는_키는_업로드하고_업로드_건수와_바이트를_계측한다")
    void 존재하지_않는_키는_업로드하고_업로드_건수와_바이트를_계측한다() {
      // given
      UploadPayload payload = uploadPayload();
      given(s3Client.headObject(any(Consumer.class))).willThrow(NoSuchKeyException.builder().build());

      // when
      writer.write(Chunk.of(payload));

      // then
      verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
      verify(metrics).countUploaded();
      verify(metrics).recordBytes(payload.compressedData().length);
    }

    @Test
    @DisplayName("이미_존재하는_키는_업로드를_건너뛰고_스킵_건수만_계측한다")
    void 이미_존재하는_키는_업로드를_건너뛰고_스킵_건수만_계측한다() {
      // given
      UploadPayload payload = uploadPayload();
      given(s3Client.headObject(any(Consumer.class))).willReturn(HeadObjectResponse.builder().build());

      // when
      writer.write(Chunk.of(payload));

      // then
      verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
      verify(metrics).countSkipped();
    }

    @Test
    @DisplayName("업로드_실패_시_실패_건수를_계측하고_LogBackupFailedException을_던진다")
    void 업로드_실패_시_실패_건수를_계측하고_LogBackupFailedException을_던진다() {
      // given
      UploadPayload payload = uploadPayload();
      given(s3Client.headObject(any(Consumer.class))).willThrow(NoSuchKeyException.builder().build());
      given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
          .willThrow(SdkException.builder().message("boom").build());

      // when & then
      assertThatThrownBy(() -> writer.write(Chunk.of(payload)))
          .isInstanceOf(LogBackupFailedException.class);
      verify(metrics).countFailed();
    }

    @Test
    @DisplayName("S3_존재_확인_실패_시_실패_건수를_계측하고_LogBackupFailedException을_던진다")
    void S3_존재_확인_실패_시_실패_건수를_계측하고_LogBackupFailedException을_던진다() {
      // given
      UploadPayload payload = uploadPayload();
      given(s3Client.headObject(any(Consumer.class)))
          .willThrow(SdkException.builder().message("throttled").build());

      // when & then
      assertThatThrownBy(() -> writer.write(Chunk.of(payload)))
          .isInstanceOf(LogBackupFailedException.class);
      verify(metrics).countFailed();
      verify(s3Client, never()).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }
  }
}
