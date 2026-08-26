package com.sprint.mission.otboo.global.file.util;

import static org.assertj.core.api.Assertions.assertThat;

import com.sprint.mission.otboo.global.file.properties.FileImplType;
import com.sprint.mission.otboo.global.file.properties.FileProperties;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("FileUrlResolver")
class FileUrlResolverTest {

  @Nested
  @DisplayName("URL 조합 (resolve)")
  class Resolve {

    @Test
    @DisplayName("baseUrl과 key를 슬래시로 이어붙인다")
    void baseUrl과_key를_슬래시로_이어붙인다() {
      // given
      FileProperties fileProperties = new FileProperties(
          FileImplType.LOCAL, "http://localhost:8080/uploads", 5242880, Set.of("jpg"), null, null);
      FileUrlResolver fileUrlResolver = new FileUrlResolver(fileProperties);

      // when
      String result = fileUrlResolver.resolve("profile/uuid.jpg");

      // then
      assertThat(result).isEqualTo("http://localhost:8080/uploads/profile/uuid.jpg");
    }

    @Test
    @DisplayName("baseUrl 끝에 슬래시가 있어도 중복되지 않는다")
    void baseUrl_끝에_슬래시가_있어도_중복되지_않는다() {
      // given
      FileProperties fileProperties = new FileProperties(
          FileImplType.LOCAL, "http://localhost:8080/uploads/", 5242880, Set.of("jpg"), null, null);
      FileUrlResolver fileUrlResolver = new FileUrlResolver(fileProperties);

      // when
      String result = fileUrlResolver.resolve("profile/uuid.jpg");

      // then
      assertThat(result).isEqualTo("http://localhost:8080/uploads/profile/uuid.jpg");
    }

    @Test
    @DisplayName("key 앞에 슬래시가 있어도 중복되지 않는다")
    void key_앞에_슬래시가_있어도_중복되지_않는다() {
      // given
      FileProperties fileProperties = new FileProperties(
          FileImplType.LOCAL, "http://localhost:8080/uploads", 5242880, Set.of("jpg"), null, null);
      FileUrlResolver fileUrlResolver = new FileUrlResolver(fileProperties);

      // when
      String result = fileUrlResolver.resolve("/profile/uuid.jpg");

      // then
      assertThat(result).isEqualTo("http://localhost:8080/uploads/profile/uuid.jpg");
    }

    @Test
    @DisplayName("key가 null이면 null을 반환한다")
    void key가_null이면_null을_반환한다() {
      // given
      FileProperties fileProperties = new FileProperties(
          FileImplType.LOCAL, "http://localhost:8080/uploads", 5242880, Set.of("jpg"), null, null);
      FileUrlResolver fileUrlResolver = new FileUrlResolver(fileProperties);

      // when
      String result = fileUrlResolver.resolve(null);

      // then
      assertThat(result).isNull();
    }
  }

  @Nested
  @DisplayName("key 추출 (extractKey)")
  class ExtractKey {

    @Test
    @DisplayName("resolve로 만든 URL에서 baseUrl 접두어를 떼고 key만 반환한다")
    void resolve로_만든_URL에서_baseUrl_접두어를_떼고_key만_반환한다() {
      // given
      FileProperties fileProperties = new FileProperties(
          FileImplType.LOCAL, "http://localhost:8080/uploads", 5242880, Set.of("jpg"), null, null);
      FileUrlResolver fileUrlResolver = new FileUrlResolver(fileProperties);

      // when
      String result = fileUrlResolver.extractKey("http://localhost:8080/uploads/profile/uuid.jpg");

      // then
      assertThat(result).isEqualTo("profile/uuid.jpg");
    }

    @Test
    @DisplayName("baseUrl 끝에 슬래시가 있어도 정상적으로 key를 추출한다")
    void baseUrl_끝에_슬래시가_있어도_정상적으로_key를_추출한다() {
      // given
      FileProperties fileProperties = new FileProperties(
          FileImplType.LOCAL, "http://localhost:8080/uploads/", 5242880, Set.of("jpg"), null, null);
      FileUrlResolver fileUrlResolver = new FileUrlResolver(fileProperties);

      // when
      String result = fileUrlResolver.extractKey("http://localhost:8080/uploads/profile/uuid.jpg");

      // then
      assertThat(result).isEqualTo("profile/uuid.jpg");
    }

    @Test
    @DisplayName("baseUrl로 시작하지 않으면 입력값을 그대로 반환한다")
    void baseUrl로_시작하지_않으면_입력값을_그대로_반환한다() {
      // given
      FileProperties fileProperties = new FileProperties(
          FileImplType.LOCAL, "http://localhost:8080/uploads", 5242880, Set.of("jpg"), null, null);
      FileUrlResolver fileUrlResolver = new FileUrlResolver(fileProperties);

      // when
      String result = fileUrlResolver.extractKey("profile/uuid.jpg");

      // then
      assertThat(result).isEqualTo("profile/uuid.jpg");
    }

    @Test
    @DisplayName("url이 null이면 null을 반환한다")
    void url이_null이면_null을_반환한다() {
      // given
      FileProperties fileProperties = new FileProperties(
          FileImplType.LOCAL, "http://localhost:8080/uploads", 5242880, Set.of("jpg"), null, null);
      FileUrlResolver fileUrlResolver = new FileUrlResolver(fileProperties);

      // when
      String result = fileUrlResolver.extractKey(null);

      // then
      assertThat(result).isNull();
    }
  }
}
