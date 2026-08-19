package com.sprint.mission.otboo.global.file.validator;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.mission.otboo.global.file.properties.FileImplType;
import com.sprint.mission.otboo.global.file.properties.FileProperties;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.boot.servlet.autoconfigure.MultipartProperties;
import org.springframework.util.unit.DataSize;

@DisplayName("FileUploadSizeConsistencyValidator")
class FileUploadSizeConsistencyValidatorTest {

  private static FileProperties fileProperties(long maxSizeBytes) {
    return new FileProperties(FileImplType.LOCAL, "http://localhost:8080/uploads", maxSizeBytes,
        Set.of("jpg", "png"), null, null);
  }

  private static MultipartProperties multipartProperties(DataSize maxFileSize) {
    MultipartProperties properties = new MultipartProperties();
    properties.setMaxFileSize(maxFileSize);
    return properties;
  }

  @Nested
  @DisplayName("업로드 크기 설정 정합성 검증")
  class Validate {

    @Test
    @DisplayName("실패_multipart_max_file_size가_otboo_file_max_size_bytes보다_작으면_예외가_발생한다")
    void 실패_multipart_max_file_size가_otboo_file_max_size_bytes보다_작으면_예외가_발생한다() {
      // given
      FileProperties fileProperties = fileProperties(10 * 1024 * 1024);
      MultipartProperties multipartProperties = multipartProperties(DataSize.ofMegabytes(5));
      FileUploadSizeConsistencyValidator validator =
          new FileUploadSizeConsistencyValidator(fileProperties, multipartProperties);

      // when & then
      assertThatThrownBy(validator::validate)
          .isInstanceOf(IllegalStateException.class);
    }

    @Test
    @DisplayName("성공_두_설정값이_같으면_예외가_발생하지_않는다")
    void 성공_두_설정값이_같으면_예외가_발생하지_않는다() {
      // given
      FileProperties fileProperties = fileProperties(5 * 1024 * 1024);
      MultipartProperties multipartProperties = multipartProperties(DataSize.ofMegabytes(5));
      FileUploadSizeConsistencyValidator validator =
          new FileUploadSizeConsistencyValidator(fileProperties, multipartProperties);

      // when & then
      assertThatCode(validator::validate).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("성공_multipart_max_file_size가_더_크면_예외가_발생하지_않는다")
    void 성공_multipart_max_file_size가_더_크면_예외가_발생하지_않는다() {
      // given
      FileProperties fileProperties = fileProperties(5 * 1024 * 1024);
      MultipartProperties multipartProperties = multipartProperties(DataSize.ofMegabytes(10));
      FileUploadSizeConsistencyValidator validator =
          new FileUploadSizeConsistencyValidator(fileProperties, multipartProperties);

      // when & then
      assertThatCode(validator::validate).doesNotThrowAnyException();
    }
  }
}
