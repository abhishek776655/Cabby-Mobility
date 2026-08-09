package com.smartmobility.notification_service.service.impl;

import com.smartmobility.notification_service.entity.NotificationEntity;
import com.smartmobility.notification_service.repository.NotificationRepository;
import com.smartmobility.notification_service.service.NotificationDeliveryService;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Slf4j
@Service
@RequiredArgsConstructor
public class NotificationDeliveryServiceImpl implements NotificationDeliveryService {

    private final NotificationRepository repository;
    private final MeterRegistry meterRegistry;

    @Override
    @Transactional
    public void deliver(String eventId, Long userId, String eventType, String channel, String message) {
        if (repository.existsByEventId(eventId)) {
            log.info("Notification for event {} already processed, skipping.", eventId);
            return;
        }

        log.info("Would notify user {} via {}: {}", userId, channel, message);

        try {
            NotificationEntity notification = NotificationEntity.builder()
                    .eventId(eventId)
                    .userId(userId)
                    .channel(channel)
                    .eventType(eventType)
                    .message(message)
                    .status("SENT")
                    .createdAt(LocalDateTime.now())
                    .build();

            repository.save(notification);
            meterRegistry.counter("business.notifications.sent").increment();
            
        } catch (DataIntegrityViolationException e) {
            log.info("Duplicate event detected during save for event {}: {}", eventId, e.getMessage());
            // It's a duplicate, not a failure, so we don't increment the failure metric.
        } catch (Exception e) {
            log.error("Failed to save notification for event {}", eventId, e);
            meterRegistry.counter("business.notifications.failed").increment();
            
            // Attempt to save failure status if possible
            try {
                NotificationEntity failedNotification = NotificationEntity.builder()
                        .eventId(eventId)
                        .userId(userId)
                        .channel(channel)
                        .eventType(eventType)
                        .message(message)
                        .status("FAILED")
                        .createdAt(LocalDateTime.now())
                        .build();
                repository.save(failedNotification);
            } catch (Exception innerException) {
                log.error("Also failed to save FAILED status for event {}", eventId, innerException);
            }
        }
    }
}
