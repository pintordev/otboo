package com.sprint.mission.otboo.global.file.validator;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.mock;

import com.sprint.mission.otboo.global.file.exception.EmptyFileException;
import com.sprint.mission.otboo.global.file.exception.FileTooLargeException;
import com.sprint.mission.otboo.global.file.exception.InvalidFileTypeException;
import com.sprint.mission.otboo.global.file.properties.FileImplType;
import com.sprint.mission.otboo.global.file.properties.FileProperties;
import java.io.IOException;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@DisplayName("FileValidator")
class FileValidatorTest {

  private static final byte[] JPEG_MAGIC_BYTES = {
      (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10,
      0x4A, 0x46, 0x49, 0x46, 0x00, 0x01
  };

  private static final byte[] PNG_MAGIC_BYTES = {
      (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
  };

  private final FileProperties fileProperties = new FileProperties(
      FileImplType.LOCAL, "http://localhost:8080/uploads", 1024, Set.of("jpg", "png"), null, null);

  private final FileValidator fileValidator = new FileValidator(fileProperties);

  @Nested
  @DisplayName("파일 검증 (validate)")
  class Validate {

    @Test
    @DisplayName("파일이 null이면 EmptyFileException을 던진다")
    void 파일이_null이면_EmptyFileException을_던진다() {
      // when & then
      assertThatThrownBy(() -> fileValidator.validate(null))
          .isInstanceOf(EmptyFileException.class);
    }

    @Test
    @DisplayName("파일이 비어있으면 EmptyFileException을 던진다")
    void 파일이_비어있으면_EmptyFileException을_던진다() {
      // given
      MultipartFile file = new MockMultipartFile("file", "empty.jpg", "image/jpeg", new byte[0]);

      // when & then
      assertThatThrownBy(() -> fileValidator.validate(file))
          .isInstanceOf(EmptyFileException.class);
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
    @DisplayName("허용된 확장자와 실제 콘텐츠가 일치하면 예외 없이 통과한다")
    void 허용된_확장자와_실제_콘텐츠가_일치하면_예외_없이_통과한다() {
      // given
      MultipartFile file = new MockMultipartFile("file", "profile.jpg", "image/jpeg", JPEG_MAGIC_BYTES);

      // when & then
      assertThatCode(() -> fileValidator.validate(file)).doesNotThrowAnyException();
    }
  }

  @Nested
  @DisplayName("확장자-콘텐츠 일치 검증")
  class ContentMatchesExtension {

    @Test
    @DisplayName("확장자는 jpg인데 실제 내용이 png면 InvalidFileTypeException을 던진다")
    void 확장자는_jpg인데_실제_내용이_png면_InvalidFileTypeException을_던진다() {
      // given
      MultipartFile file = new MockMultipartFile("file", "fake.jpg", "image/jpeg", PNG_MAGIC_BYTES);

      // when & then
      assertThatThrownBy(() -> fileValidator.validate(file))
          .isInstanceOf(InvalidFileTypeException.class);
    }

    @Test
    @DisplayName("확장자와 무관한 임의 바이트면 InvalidFileTypeException을 던진다")
    void 확장자와_무관한_임의_바이트면_InvalidFileTypeException을_던진다() {
      // given
      MultipartFile file = new MockMultipartFile("file", "fake.jpg", "image/jpeg",
          new byte[]{1, 2, 3, 4, 5});

      // when & then
      assertThatThrownBy(() -> fileValidator.validate(file))
          .isInstanceOf(InvalidFileTypeException.class);
    }

    @Test
    @DisplayName("확장자는 png인데 실제 내용이 jpeg면 InvalidFileTypeException을 던진다")
    void 확장자는_png인데_실제_내용이_jpeg면_InvalidFileTypeException을_던진다() {
      // given
      MultipartFile file = new MockMultipartFile("file", "fake.png", "image/png", JPEG_MAGIC_BYTES);

      // when & then
      assertThatThrownBy(() -> fileValidator.validate(file))
          .isInstanceOf(InvalidFileTypeException.class);
    }

    @Test
    @DisplayName("확장자와 실제 png 콘텐츠가 일치하면 예외 없이 통과한다")
    void 확장자와_실제_png_콘텐츠가_일치하면_예외_없이_통과한다() {
      // given
      MultipartFile file = new MockMultipartFile("file", "profile.png", "image/png", PNG_MAGIC_BYTES);

      // when & then
      assertThatCode(() -> fileValidator.validate(file)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("콘텐츠를 읽는 중 IOException이 발생하면 InvalidFileTypeException을 던진다")
    void 콘텐츠를_읽는_중_IOException이_발생하면_InvalidFileTypeException을_던진다() throws IOException {
      // given
      MultipartFile file = mock(MultipartFile.class);
      given(file.isEmpty()).willReturn(false);
      given(file.getSize()).willReturn(3L);
      given(file.getOriginalFilename()).willReturn("profile.jpg");
      willThrow(new IOException("스트림 읽기 실패")).given(file).getInputStream();

      // when & then
      assertThatThrownBy(() -> fileValidator.validate(file))
          .isInstanceOf(InvalidFileTypeException.class);
    }
  }
}
