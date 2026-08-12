package com.sprint.mission.otboo.global.file.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Locale;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

@DisplayName("FileExtensionUtils")
class FileExtensionUtilsTest {

  @Nested
  @DisplayName("확장자 추출 (extract)")
  class Extract {

    @Test
    @DisplayName("파일명에 확장자가 있으면 소문자로 변환해서 추출한다")
    void 파일명에_확장자가_있으면_소문자로_변환해서_추출한다() {
      // given
      String filename = "profile.JPG";

      // when
      Optional<String> result = FileExtensionUtils.extract(filename);

      // then
      assertThat(result).contains("jpg");
    }

    @Test
    @DisplayName("점이 없으면 빈 Optional을 반환한다")
    void 점이_없으면_빈_Optional을_반환한다() {
      // given
      String filename = "profile";

      // when
      Optional<String> result = FileExtensionUtils.extract(filename);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("점으로 끝나면 빈 Optional을 반환한다")
    void 점으로_끝나면_빈_Optional을_반환한다() {
      // given
      String filename = "profile.";

      // when
      Optional<String> result = FileExtensionUtils.extract(filename);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("파일명이 null이면 빈 Optional을 반환한다")
    void 파일명이_null이면_빈_Optional을_반환한다() {
      // when
      Optional<String> result = FileExtensionUtils.extract(null);

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("파일명이 공백이면 빈 Optional을 반환한다")
    void 파일명이_공백이면_빈_Optional을_반환한다() {
      // when
      Optional<String> result = FileExtensionUtils.extract("   ");

      // then
      assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("터키어 로케일에서도 확장자를 올바르게 소문자로 변환한다")
    void 터키어_로케일에서도_확장자를_올바르게_소문자로_변환한다() {
      // given
      Locale defaultLocale = Locale.getDefault();
      Locale.setDefault(Locale.forLanguageTag("tr-TR"));

      try {
        // when
        Optional<String> result = FileExtensionUtils.extract("profile.GIF");

        // then
        assertThat(result).contains("gif");
      } finally {
        Locale.setDefault(defaultLocale);
      }
    }
  }
}
