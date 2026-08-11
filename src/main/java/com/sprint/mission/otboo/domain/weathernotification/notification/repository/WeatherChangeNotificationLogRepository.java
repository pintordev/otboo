package com.sprint.mission.otboo.domain.weathernotification.notification.repository;

import com.sprint.mission.otboo.domain.weathernotification.notification.entity.WeatherChangeNotificationLog;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface WeatherChangeNotificationLogRepository
    extends JpaRepository<WeatherChangeNotificationLog, UUID> {

}