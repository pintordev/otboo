package com.sprint.mission.otboo.global.file.validator;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.mission.otboo.global.file.exception.FileTooLargeException;
import com.sprint.mission.otboo.global.file.exception.InvalidFileTypeException;
import com.sprint.mission.otboo.global.file.properties.FileProperties;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@DisplayName("FileValidator")
class FileValidatorTest {

  private final FileProperties fileProperties = new FileProperties(
      "local", "http://localhost:8080/uploads", 1024, Set.of("jpg", "png"), null, null);

  private final FileValidator fileValidator = new FileValidator(fileProperties);

  @Nested
  @DisplayName("파일 검증 (validate)")
  class Validate {

    @Test
    @DisplayName("파일이 null이면 InvalidFileTypeException을 던진다")
    void 파일이_null이면_InvalidFileTypeException을_던진다() {
      // when & then
      assertThatThrownBy(() -> fileValidator.validate(null))
          .isInstanceOf(InvalidFileTypeException.class);
    }

    @Test
    @DisplayName("파일이 비어있으면 InvalidFileTypeException을 던진다")
    void 파일이_비어있으면_InvalidFileTypeException을_던진다() {
      // given
      MultipartFile file = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);

      // when & then
      assertThatThrownBy(() -> fileValidator.validate(file))
          .isInstanceOf(InvalidFileTypeException.class);
    }

    @Test
    @DisplayName("파일 크기가 허용 용량을 초과하면 FileTooLargeException을 던진다")
    void 파일_크기가_허용_용량을_초과하면_FileTooLargeException을_던진다() {
      // given
      byte[] content = new byte[2048];
      MultipartFile file = new MockMultipartFile("file", "large.jpg", "image/jpeg", content);

      // when & then
      assertThatThrownBy(() -> fileValidator.validate(file))
          .isInstanceOf(FileTooLargeException.class);
    }

    @Test
    @DisplayName("허용되지 않는 확장자면 InvalidFileTypeException을 던진다")
    void 허용되지_않는_확장자면_InvalidFileTypeException을_던진다() {
      // given
      MultipartFile file = new MockMultipartFile("file", "malware.exe", "application/octet-stream",
          new byte[]{1, 2, 3});

      // when & then
      assertThatThrownBy(() -> fileValidator.validate(file))
          .isInstanceOf(InvalidFileTypeException.class);
    }

    @Test
    @DisplayName("허용된 확장자와 크기면 예외 없이 통과한다")
    void 허용된_확장자와_크기면_예외_없이_통과한다() {
      // given
      MultipartFile file = new MockMultipartFile("file", "profile.jpg", "image/jpeg",
          new byte[]{1, 2, 3});

      // when & then
      assertThatCode(() -> fileValidator.validate(file)).doesNotThrowAnyException();
    }
  }
}
