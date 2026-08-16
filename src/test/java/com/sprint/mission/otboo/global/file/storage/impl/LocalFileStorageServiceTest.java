package com.sprint.mission.otboo.global.file.storage.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sprint.mission.otboo.global.file.exception.InvalidFilePathException;
import com.sprint.mission.otboo.global.file.exception.InvalidFileTypeException;
import com.sprint.mission.otboo.global.file.properties.FileImplType;
import com.sprint.mission.otboo.global.file.properties.FileProperties;
import com.sprint.mission.otboo.global.file.validator.FileValidator;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;

@DisplayName("LocalFileStorageService")
class LocalFileStorageServiceTest {

  @TempDir
  Path tempDir;

  private LocalFileStorageService buildService() {
    FileProperties fileProperties = new FileProperties(
        FileImplType.LOCAL, "http://localhost:8080/uploads", 1024 * 1024, Set.of("jpg", "png"),
        new FileProperties.Local(tempDir.toString()), null);
    return new LocalFileStorageService(fileProperties, new FileValidator(fileProperties));
  }

  @Nested
  @DisplayName("파일 저장 (store)")
  class Store {

    @Test
    @DisplayName("정상 파일을 저장하면 domain_UUID_확장자 형식의 key를 반환하고 실제로 디스크에 저장한다")
    void 정상_파일을_저장하면_domain_UUID_확장자_형식의_key를_반환하고_실제로_디스크에_저장한다() {
      // given
      LocalFileStorageService localFileStorageService = buildService();
      MultipartFile file = new MockMultipartFile("file", "profile.jpg", "image/jpeg",
          new byte[]{1, 2, 3});

      // when
      String key = localFileStorageService.store(file, "profile");

      // then
      assertThat(key).startsWith("profile/").endsWith(".jpg");
      assertThat(Files.exists(tempDir.resolve(key))).isTrue();
    }

    @Test
    @DisplayName("허용되지 않는 확장자면 InvalidFileTypeException을 던지고 파일을 저장하지 않는다")
    void 허용되지_않는_확장자면_InvalidFileTypeException을_던지고_파일을_저장하지_않는다() {
      // given
      LocalFileStorageService localFileStorageService = buildService();
      MultipartFile file = new MockMultipartFile("file", "malware.exe", "application/octet-stream",
          new byte[]{1, 2, 3});

      // when & then
      assertThatThrownBy(() -> localFileStorageService.store(file, "profile"))
          .isInstanceOf(InvalidFileTypeException.class);
    }

    @Test
    @DisplayName("domain에 경로 조작이 포함되면 InvalidFilePathException을 던진다")
    void domain에_경로_조작이_포함되면_InvalidFilePathException을_던진다() {
      // given
      LocalFileStorageService localFileStorageService = buildService();
      MultipartFile file = new MockMultipartFile("file", "profile.jpg", "image/jpeg",
          new byte[]{1, 2, 3});

      // when & then
      assertThatThrownBy(() -> localFileStorageService.store(file, "../../etc"))
          .isInstanceOf(InvalidFilePathException.class);
    }
  }

  @Nested
  @DisplayName("파일 삭제 (delete)")
  class Delete {

    @Test
    @DisplayName("존재하는 파일을 삭제하면 디스크에서 사라진다")
    void 존재하는_파일을_삭제하면_디스크에서_사라진다() {
      // given
      LocalFileStorageService localFileStorageService = buildService();
      MultipartFile file = new MockMultipartFile("file", "profile.jpg", "image/jpeg",
          new byte[]{1, 2, 3});
      String key = localFileStorageService.store(file, "profile");

      // when
      localFileStorageService.delete(key);

      // then
      assertThat(Files.exists(tempDir.resolve(key))).isFalse();
    }

    @Test
    @DisplayName("key가 null이면 아무 것도 하지 않는다")
    void key가_null이면_아무_것도_하지_않는다() {
      // given
      LocalFileStorageService localFileStorageService = buildService();

      // when & then
      assertThatCode(() -> localFileStorageService.delete(null)).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("존재하지 않는 파일이어도 예외 없이 조용히 넘어간다")
    void 존재하지_않는_파일이어도_예외_없이_조용히_넘어간다() {
      // given
      LocalFileStorageService localFileStorageService = buildService();

      // when & then
      assertThatCode(() -> localFileStorageService.delete("profile/not-exists.jpg"))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("경로 조작이 포함된 key면 예외 없이 무시하고 삭제를 시도하지 않는다")
    void 경로_조작이_포함된_key면_예외_없이_무시하고_삭제를_시도하지_않는다() {
      // given
      LocalFileStorageService localFileStorageService = buildService();

      // when & then
      assertThatCode(() -> localFileStorageService.delete("../../../../etc/passwd"))
          .doesNotThrowAnyException();
    }
  }
}
