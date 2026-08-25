package com.sprint.mission.otboo.domain.weathernotification.notification.repository;

import com.sprint.mission.otboo.domain.weathernotification.notification.entity.NotificationOutbox;
import com.sprint.mission.otboo.domain.weathernotification.notification.entity.NotificationOutboxStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationOutboxRepository extends JpaRepository<NotificationOutbox, UUID> {

  List<NotificationOutbox> findByStatusOrderByCreatedAtAsc(
      NotificationOutboxStatus status, Pageable pageable);
}