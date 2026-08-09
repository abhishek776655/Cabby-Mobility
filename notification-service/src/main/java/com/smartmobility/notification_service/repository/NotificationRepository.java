package com.smartmobility.notification_service.repository;

import com.smartmobility.notification_service.entity.NotificationEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<NotificationEntity, Long> {
    boolean existsByEventId(String eventId);
}
