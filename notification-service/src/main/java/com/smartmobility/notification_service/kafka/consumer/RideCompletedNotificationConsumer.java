package com.smartmobility.notification_service.kafka.consumer;

import com.smartmobility.notification_service.event.RideCompletedEvent;
import com.smartmobility.notification_service.service.NotificationDeliveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Slf4j
@Component
@RequiredArgsConstructor
public class RideCompletedNotificationConsumer {

    private final ObjectMapper objectMapper;
    private final NotificationDeliveryService deliveryService;

    @KafkaListener(topics = "ride-completed", groupId = "notification-service-group", containerFactory = "kafkaListenerContainerFactory")
    public void consume(String message) {
        log.info("Received ride-completed event");
        try {
            RideCompletedEvent event = objectMapper.readValue(message, RideCompletedEvent.class);
            deliveryService.deliver(
                event.getEventId(), 
                event.getRiderUserId(), 
                "RIDE_COMPLETED", 
                "PUSH", 
                "Your ride is complete"
            );
        } catch (Exception e) {
            log.error("Failed to parse RideCompletedEvent: {}", message, e);
            throw new IllegalArgumentException("Invalid message format", e);
        }
    }
}
