package com.sprint.mission.otboo.domain.weathernotification.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.navercorp.fixturemonkey.FixtureMonkey;
import com.navercorp.fixturemonkey.api.introspector.FieldReflectionArbitraryIntrospector;
import com.sprint.mission.otboo.domain.weathernotification.notification.entity.Notification;
import com.sprint.mission.otboo.global.event.NotificationLevel;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;

@ExtendWith(MockitoExtension.class)
class NotificationBatchWriterTest {

  private static final FixtureMonkey FIXTURE_MONKEY = FixtureMonkey.builder()
      .objectIntrospector(FieldReflectionArbitraryIntrospector.INSTANCE)
      .defaultNotNull(true)
      .build();

  @Mock
  private JdbcTemplate jdbcTemplate;

  private NotificationBatchWriter notificationBatchWriter;

  @BeforeEach
  void setUp() {
    notificationBatchWriter = new NotificationBatchWriter(jdbcTemplate);
  }

  @Nested
  @DisplayName("saveAll")
  class SaveAll {

    @Test
    @DisplayName("빈_목록이면_batchUpdate를_호출하지_않는다")
    void 빈_목록이면_batchUpdate를_호출하지_않는다() {
      // when
      notificationBatchWriter.saveAll(List.of());

      // then
      verify(jdbcTemplate, never()).batchUpdate(anyString(), any(BatchPreparedStatementSetter.class));
    }

    @Test
    @DisplayName("INSERT_SQL로_notifications에_배치_삽입한다")
    void INSERT_SQL로_notifications에_배치_삽입한다() {
      // given
      Notification notification = FIXTURE_MONKEY.giveMeBuilder(Notification.class)
          .set("eventId", UUID.randomUUID())
          .set("receiverId", UUID.randomUUID())
          .set("title", "제목")
          .set("content", "내용")
          .set("level", NotificationLevel.INFO)
          .sample();
      given(jdbcTemplate.batchUpdate(anyString(), any(BatchPreparedStatementSetter.class)))
          .willReturn(new int[]{1});

      // when
      notificationBatchWriter.saveAll(List.of(notification));

      // then
      ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
      verify(jdbcTemplate).batchUpdate(sqlCaptor.capture(), any(BatchPreparedStatementSetter.class));
      assertThat(sqlCaptor.getValue())
          .contains("INSERT INTO notifications")
          .contains("event_id", "receiver_id", "title", "content", "level");
    }

    @Test
    @DisplayName("각_알림의_필드를_PreparedStatement에_바인딩한다")
    void 각_알림의_필드를_PreparedStatement에_바인딩한다() throws Exception {
      // given
      UUID eventId = UUID.randomUUID();
      UUID receiverId = UUID.randomUUID();
      Notification notification = FIXTURE_MONKEY.giveMeBuilder(Notification.class)
          .set("eventId", eventId)
          .set("receiverId", receiverId)
          .set("title", "제목")
          .set("content", "내용")
          .set("level", NotificationLevel.WARNING)
          .sample();
      given(jdbcTemplate.batchUpdate(anyString(), any(BatchPreparedStatementSetter.class)))
          .willReturn(new int[]{1});

      // when
      notificationBatchWriter.saveAll(List.of(notification));

      // then
      ArgumentCaptor<BatchPreparedStatementSetter> setterCaptor =
          ArgumentCaptor.forClass(BatchPreparedStatementSetter.class);
      verify(jdbcTemplate).batchUpdate(anyString(), setterCaptor.capture());
      PreparedStatement preparedStatement = mock(PreparedStatement.class);
      setterCaptor.getValue().setValues(preparedStatement, 0);
      verify(preparedStatement).setObject(2, eventId, Types.OTHER);
      verify(preparedStatement).setObject(3, receiverId);
      verify(preparedStatement).setString(4, "제목");
      verify(preparedStatement).setString(5, "내용");
      verify(preparedStatement).setString(6, "WARNING");
    }

    @Test
    @DisplayName("배치_크기는_전달받은_알림_수와_같다")
    void 배치_크기는_전달받은_알림_수와_같다() throws Exception {
      // given
      Notification notification1 = FIXTURE_MONKEY.giveMeBuilder(Notification.class)
          .set("eventId", UUID.randomUUID())
          .sample();
      Notification notification2 = FIXTURE_MONKEY.giveMeBuilder(Notification.class)
          .set("eventId", UUID.randomUUID())
          .sample();
      given(jdbcTemplate.batchUpdate(anyString(), any(BatchPreparedStatementSetter.class)))
          .willReturn(new int[]{1, 1});

      // when
      notificationBatchWriter.saveAll(List.of(notification1, notification2));

      // then
      ArgumentCaptor<BatchPreparedStatementSetter> setterCaptor =
          ArgumentCaptor.forClass(BatchPreparedStatementSetter.class);
      verify(jdbcTemplate).batchUpdate(anyString(), setterCaptor.capture());
      assertThat(setterCaptor.getValue().getBatchSize()).isEqualTo(2);
    }
  }
}