package com.sprint.mission.otboo.domain.weathernotification.notification.repository;

import com.sprint.mission.otboo.domain.weathernotification.notification.entity.Notification;
import com.sprint.mission.otboo.domain.weathernotification.notification.repository.querydsl.NotificationCustomRepository;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository
    extends JpaRepository<Notification, UUID>, NotificationCustomRepository {

}