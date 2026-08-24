package com.sprint.mission.otboo.domain.clothesrecommend.clothes.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

@DisplayName("의상 추출 실패 예외")
class ClothesExtractionFailedExceptionTest {

  @Nested
  @DisplayName("팩토리")
  class Factory {

    @Test
    @DisplayName("pageFetchFailed는 url을 details에 담고 원인 예외를 유지한다")
    void pageFetchFailed는_url을_details에_담고_원인_예외를_유지한다() {
      // given
      String url = "https://www.musinsa.com/products/1";
      Throwable cause = new RuntimeException("연결 실패");

      // when
      ClothesExtractionFailedException exception =
          ClothesExtractionFailedException.pageFetchFailed(url, cause);

      // then
      assertThat(exception.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
      assertThat(exception.getMessage()).isEqualTo("구매 페이지를 불러올 수 없습니다.");
      assertThat(exception.getDetails()).isEqualTo(Map.of("url", url));
      assertThat(exception.getCause()).isEqualTo(cause);
    }

    @Test
    @DisplayName("llmCallFailed는 details가 비어 있고 원인 예외를 유지한다")
    void llmCallFailed는_details가_비어_있고_원인_예외를_유지한다() {
      // given
      Throwable cause = new RuntimeException("LLM 호출 실패");

      // when
      ClothesExtractionFailedException exception =
          ClothesExtractionFailedException.llmCallFailed(cause);

      // then
      assertThat(exception.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
      assertThat(exception.getMessage()).isEqualTo("LLM 호출에 실패했습니다.");
      assertThat(exception.getDetails()).isEmpty();
      assertThat(exception.getCause()).isEqualTo(cause);
    }

    @Test
    @DisplayName("llmParseFailed는 details가 비어 있고 원인 예외가 없다")
    void llmParseFailed는_details가_비어_있고_원인_예외가_없다() {
      // when
      ClothesExtractionFailedException exception =
          ClothesExtractionFailedException.llmParseFailed();

      // then
      assertThat(exception.getStatus()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
      assertThat(exception.getMessage()).isEqualTo("LLM 응답을 파싱할 수 없습니다.");
      assertThat(exception.getDetails()).isEmpty();
      assertThat(exception.getCause()).isNull();
    }
  }
}
