package com.sprint.mission.otboo.global.file.storage.impl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.sprint.mission.otboo.global.file.exception.FileStorageException;
import com.sprint.mission.otboo.global.file.properties.FileImplType;
import com.sprint.mission.otboo.global.file.properties.FileProperties;
import com.sprint.mission.otboo.global.file.validator.FileValidator;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Exception;

@ExtendWith(MockitoExtension.class)
@DisplayName("S3FileStorageService")
class S3FileStorageServiceTest {

  @Mock
  private S3Client s3Client;

  private final FileProperties fileProperties = new FileProperties(
      FileImplType.S3, "https://cdn.otboo.us", 1024 * 1024, Set.of("jpg", "png"),
      null, new FileProperties.S3("otboo-image", "ap-northeast-2"));

  private S3FileStorageService buildService() {
    return new S3FileStorageService(s3Client, fileProperties, new FileValidator(fileProperties));
  }

  @Nested
  @DisplayName("파일 저장 (store)")
  class Store {

    @Test
    @DisplayName("정상 파일을 저장하면 putObject를 호출하고 domain_UUID_확장자 형식의 key를 반환한다")
    void 정상_파일을_저장하면_putObject를_호출하고_domain_UUID_확장자_형식의_key를_반환한다() {
      // given
      S3FileStorageService s3FileStorageService = buildService();
      MultipartFile file = new MockMultipartFile("file", "profile.jpg", "image/jpeg",
          new byte[]{1, 2, 3});

      // when
      String key = s3FileStorageService.store(file, "profile");

      // then
      assertThat(key).startsWith("profile/").endsWith(".jpg");
      verify(s3Client).putObject(any(PutObjectRequest.class), any(RequestBody.class));
    }

    @Test
    @DisplayName("putObject 호출 중 S3Exception이 발생하면 FileStorageException으로 변환한다")
    void putObject_호출_중_S3Exception이_발생하면_FileStorageException으로_변환한다() {
      // given
      S3FileStorageService s3FileStorageService = buildService();
      MultipartFile file = new MockMultipartFile("file", "profile.jpg", "image/jpeg",
          new byte[]{1, 2, 3});
      given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
          .willThrow(S3Exception.builder().message("access denied").build());

      // when & then
      assertThatThrownBy(() -> s3FileStorageService.store(file, "profile"))
          .isInstanceOf(FileStorageException.class);
    }

    @Test
    @DisplayName("putObject 호출 중 SdkClientException이 발생하면 FileStorageException으로 변환한다")
    void putObject_호출_중_SdkClientException이_발생하면_FileStorageException으로_변환한다() {
      // given
      S3FileStorageService s3FileStorageService = buildService();
      MultipartFile file = new MockMultipartFile("file", "profile.jpg", "image/jpeg",
          new byte[]{1, 2, 3});
      given(s3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
          .willThrow(SdkClientException.create("자격 증명을 찾을 수 없습니다"));

      // when & then
      assertThatThrownBy(() -> s3FileStorageService.store(file, "profile"))
          .isInstanceOf(FileStorageException.class);
    }
  }

  @Nested
  @DisplayName("파일 삭제 (delete)")
  class Delete {

    @Test
    @DisplayName("존재하는 key를 삭제하면 deleteObject를 호출한다")
    void 존재하는_key를_삭제하면_deleteObject를_호출한다() {
      // given
      S3FileStorageService s3FileStorageService = buildService();

      // when
      s3FileStorageService.delete("profile/uuid.jpg");

      // then
      verify(s3Client).deleteObject(any(DeleteObjectRequest.class));
    }

    @Test
    @DisplayName("deleteObject 호출 중 S3Exception이 발생해도 예외를 던지지 않는다")
    void deleteObject_호출_중_S3Exception이_발생해도_예외를_던지지_않는다() {
      // given
      S3FileStorageService s3FileStorageService = buildService();
      given(s3Client.deleteObject(any(DeleteObjectRequest.class)))
          .willThrow(S3Exception.builder().message("access denied").build());

      // when & then
      assertThatCode(() -> s3FileStorageService.delete("profile/uuid.jpg"))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("deleteObject 호출 중 SdkClientException이 발생해도 예외를 던지지 않는다")
    void deleteObject_호출_중_SdkClientException이_발생해도_예외를_던지지_않는다() {
      // given
      S3FileStorageService s3FileStorageService = buildService();
      given(s3Client.deleteObject(any(DeleteObjectRequest.class)))
          .willThrow(SdkClientException.create("자격 증명을 찾을 수 없습니다"));

      // when & then
      assertThatCode(() -> s3FileStorageService.delete("profile/uuid.jpg"))
          .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("key가 null이면 deleteObject를 호출하지 않는다")
    void key가_null이면_deleteObject를_호출하지_않는다() {
      // given
      S3FileStorageService s3FileStorageService = buildService();

      // when
      s3FileStorageService.delete(null);

      // then
      verify(s3Client, never()).deleteObject(any(DeleteObjectRequest.class));
    }
  }

  @Nested
  @DisplayName("종료 (close)")
  class Close {

    @Test
    @DisplayName("컨텍스트 종료 시 S3Client를 닫는다")
    void 컨텍스트_종료_시_S3Client를_닫는다() {
      // given
      S3FileStorageService s3FileStorageService = buildService();

      // when
      s3FileStorageService.close();

      // then
      verify(s3Client).close();
    }
  }
}
