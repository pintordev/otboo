package com.sprint.mission.otboo.global.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DefaultErrorHandler;

class KafkaConfigTest {

  private final KafkaConfig kafkaConfig = new KafkaConfig();

  @Nested
  @DisplayName("공용 에러 핸들러 구성")
  class KafkaCommonErrorHandler {

    @Test
    @DisplayName("DefaultErrorHandler로 구성된다")
    void DefaultErrorHandler로_구성된다() {
      // given
      KafkaTemplate<Object, Object> kafkaTemplate = mock(KafkaTemplate.class);

      // when
      CommonErrorHandler errorHandler = kafkaConfig.kafkaCommonErrorHandler(kafkaTemplate);

      // then
      assertThat(errorHandler).isInstanceOf(DefaultErrorHandler.class);
    }
  }
}