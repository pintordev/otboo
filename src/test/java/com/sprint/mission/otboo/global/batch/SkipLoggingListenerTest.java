package com.sprint.mission.otboo.global.batch;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

class SkipLoggingListenerTest {

  private SkipLoggingListener listener;
  private ListAppender<ILoggingEvent> appender;
  private Logger logger;

  @BeforeEach
  void setUp() {
    listener = new SkipLoggingListener();
    logger = (Logger) LoggerFactory.getLogger(SkipLoggingListener.class);
    appender = new ListAppender<>();
    appender.start();
    logger.addAppender(appender);
  }

  @AfterEach
  void tearDown() {
    logger.detachAppender(appender);
    appender.stop();
  }

  @Nested
  @DisplayName("OnSkipInRead")
  class OnSkipInRead {

    @Test
    @DisplayName("Read_단계에서_skip_발생_시_에러_로그를_남긴다")
    void Read_단계에서_skip_발생_시_에러_로그를_남긴다() {
      // when
      listener.onSkipInRead(new RuntimeException("Read 실패"));

      // then
      assertThat(appender.list).isNotEmpty();
      ILoggingEvent logEvent = appender.list.get(0);
      assertThat(logEvent.getLevel()).isEqualTo(Level.ERROR);
      assertThat(logEvent.getFormattedMessage()).contains("배치 Reader skip");
    }
  }

  @Nested
  @DisplayName("OnSkipInProcess")
  class OnSkipInProcess {

    @Test
    @DisplayName("Process_단계에서_skip_발생_시_에러_로그를_남긴다")
    void Process_단계에서_skip_발생_시_에러_로그를_남긴다() {
      // when
      listener.onSkipInProcess("item", new RuntimeException("Process 실패"));

      // then
      assertThat(appender.list).isNotEmpty();
      ILoggingEvent logEvent = appender.list.get(0);
      assertThat(logEvent.getLevel()).isEqualTo(Level.ERROR);
      assertThat(logEvent.getFormattedMessage()).contains("배치 Processor skip");
    }
  }

  @Nested
  @DisplayName("OnSkipInWrite")
  class OnSkipInWrite {

    @Test
    @DisplayName("Write_단계에서_skip_발생_시_에러_로그를_남긴다")
    void Write_단계에서_skip_발생_시_에러_로그를_남긴다() {
      // when
      listener.onSkipInWrite("item", new RuntimeException("Write 실패"));

      // then
      assertThat(appender.list).isNotEmpty();
      ILoggingEvent logEvent = appender.list.get(0);
      assertThat(logEvent.getLevel()).isEqualTo(Level.ERROR);
      assertThat(logEvent.getFormattedMessage()).contains("배치 Writer skip");
    }
  }
}