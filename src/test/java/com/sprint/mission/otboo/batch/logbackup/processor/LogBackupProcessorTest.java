package com.sprint.mission.otboo.batch.logbackup.processor;

import static org.assertj.core.api.Assertions.assertThat;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.ConstructorPropertiesArbitraryIntrospector;
import com.sprint.mission.otboo.batch.logbackup.dto.LogContent;
import com.sprint.mission.otboo.batch.logbackup.dto.UploadPayload;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LogBackupProcessorTest {

  private static final FixtureMonkey FIXTURE_MONKEY = FixtureMonkey.builder()
      .objectIntrospector(ConstructorPropertiesArbitraryIntrospector.INSTANCE)
      .build();

  @Nested
  @DisplayName("페이지 변환")
  class Process {

    private final LogBackupProcessor processor = new LogBackupProcessor();

    @Test
    @DisplayName("페이지를_gzip_압축해_그룹별_폴더의_S3_키와_함께_UploadPayload로_반환한다")
    void 페이지를_gzip_압축해_그룹별_폴더의_S3_키와_함께_UploadPayload로_반환한다() throws Exception {
      // given
      LocalDate date = LocalDate.of(2026, 8, 20);
      LogContent content = FIXTURE_MONKEY.giveMeBuilder(LogContent.class)
          .set("groupLabel", "nginx")
          .set("date", date)
          .set("lines", "line1\nline2".getBytes(StandardCharsets.UTF_8))
          .set("pageNumber", 1)
          .sample();

      // when
      UploadPayload payload = processor.process(content);

      // then — 로그 그룹(streamPrefix)별 폴더로 분리된다
      assertThat(payload.s3Key()).isEqualTo("logs/nginx/2026/08/20/nginx-20260820-001.log.gz");
      // 압축 해제하면 원본과 동일해야 한다
      byte[] decompressed;
      try (var gis = new GZIPInputStream(new ByteArrayInputStream(payload.compressedData()))) {
        decompressed = gis.readAllBytes();
      }
      assertThat(new String(decompressed, StandardCharsets.UTF_8)).isEqualTo("line1\nline2");
    }
  }
}
