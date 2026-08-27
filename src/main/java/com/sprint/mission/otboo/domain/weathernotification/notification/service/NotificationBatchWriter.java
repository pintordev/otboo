package com.sprint.mission.otboo.domain.weathernotification.notification.service;

import com.sprint.mission.otboo.domain.weathernotification.notification.entity.Notification;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// Hibernate saveAll()은 알림 건수만큼 개별 INSERT를 보낸다(hibernate.jdbc.batch_size 미설정은
// 프로젝트 전역 갭). 그 전역 설정을 켜는 결정을 기다리지 않고, WeatherWriter.saveSlots()와 동일한
// 패턴(JdbcTemplate.batchUpdate)으로 이 도메인 안에서 바로 해결한다.
@Transactional(readOnly = true)
@RequiredArgsConstructor
@Component
public class NotificationBatchWriter {

  private static final String INSERT_SQL = """
      INSERT INTO notifications (id, event_id, receiver_id, title, content, level, created_at)
      VALUES (?, ?, ?, ?, ?, ?, now())
      """;

  private final JdbcTemplate jdbcTemplate;

  @Transactional
  public void saveAll(List<Notification> notifications) {
    if (notifications.isEmpty()) {
      return;
    }
    jdbcTemplate.batchUpdate(INSERT_SQL, new BatchPreparedStatementSetter() {
      @Override
      public void setValues(PreparedStatement ps, int i) throws SQLException {
        Notification notification = notifications.get(i);
        ps.setObject(1, UUID.randomUUID());
        ps.setObject(2, notification.getEventId(), Types.OTHER);
        ps.setObject(3, notification.getReceiverId());
        ps.setString(4, notification.getTitle());
        ps.setString(5, notification.getContent());
        ps.setString(6, notification.getLevel().name());
      }

      @Override
      public int getBatchSize() {
        return notifications.size();
      }
    });
  }
}